package es.eriktorr
package trip_agent

import trip_agent.api.{HttpRoutes, TripSearchWorkflowService}
import trip_agent.application.*
import trip_agent.application.agents.tools.{ChatModelProvider, DateExtractor, EmailExtractor}
import trip_agent.application.agents.{
  AccommodationsSearchAgent,
  FlightsSearchAgent,
  MailWriterAgent,
}
import trip_agent.infrastructure.HttpClient.httpClientWith
import trip_agent.infrastructure.OllamaApiClient
import trip_agent.infrastructure.db.DatabaseMigrator

import cats.effect.unsafe.implicits.global
import cats.effect.{ExitCode, IO, Resource}
import cats.implicits.{catsSyntaxTuple2Semigroupal, toFlatMapOps}
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.persistence.jdbc.query.scaladsl.JdbcReadJournal
import org.apache.pekko.persistence.jdbc.testkit.scaladsl.SchemaUtils
import org.apache.pekko.persistence.query.PersistenceQuery
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import workflows4s.runtime.instanceengine.WorkflowInstanceEngine
import workflows4s.runtime.pekko.PekkoRuntime
import workflows4s.runtime.wakeup.SleepingKnockerUpper

object TripSearchApplication
    extends CommandIOApp(name = "trip-search", header = "Trip Search Agent"):
  override def main: Opts[IO[ExitCode]] =
    (TripSearchConfig.opts, TripSearchParams.opts).mapN:
      case (config, params) => program(config, params)

  private def program(
      config: TripSearchConfig,
      params: TripSearchParams,
  ): IO[ExitCode] =
    given actorSystem: ActorSystem[Any] = ActorSystem(Behaviors.empty, "TripSearchCluster")
    given logger: StructuredLogger[IO] = Slf4jLogger.getLogger[IO]
    (for
      _ <- Resource.eval(DatabaseMigrator(config.dbConfig).migrate)
      httpClient <- httpClientWith()
      knockerUpper <-
        SleepingKnockerUpper
          .create()
          .flatTap(_ => Resource.make(IO.unit)(_ => IO(actorSystem.terminate())))
    yield (httpClient, knockerUpper)).use: (httpClient, knockerUpper) =>
      for
        journal <- setupJournal()
        tripSearchWorkflow <-
          val chatModelProvider = ChatModelProvider(
            ollamaApiClient = OllamaApiClient.impl(
              config = config.ollamaConfig,
              httpClient = httpClient,
            ),
            config = config.ollamaConfig,
          )
          for
            chatModel <- chatModelProvider.chatModel(
              verbose = params.verbose,
            )
            tripSearchWorkflow = TripSearchWorkflow(
              accommodationsSearchAgent = AccommodationsSearchAgent.impl(
                accommodationService = AccommodationService.impl,
                chatModel = chatModel,
                dateExtractor = DateExtractor.impl(chatModel),
              ),
              flightsSearchAgent = FlightsSearchAgent.impl(
                flightService = FlightService.impl,
                chatModel = chatModel,
                dateExtractor = DateExtractor.impl(chatModel),
              ),
              mailWriterAgent = MailWriterAgent.impl(
                chatModel = chatModel,
                emailExtractor = EmailExtractor.impl(chatModel),
              ),
              mailSender = MailSender.impl(
                from = "trip.agency@gmail.com",
              ),
              bookingService = BookingService.impl,
            )
          yield tripSearchWorkflow
        runtime =
          PekkoRuntime.create[TripSearchWorkflow.TripSearchContext.Ctx](
            entityName = "trip-search",
            workflow = tripSearchWorkflow.workflow,
            initialState = TripSearchWorkflow.TripSearchState.Empty,
            engine = WorkflowInstanceEngine.builder
              .withJavaTime()
              .withWakeUps(knockerUpper)
              .withoutRegistering
              .withGreedyEvaluation
              .withLogging
              .get,
          )
        _ <- IO(runtime.initializeShard())
        _ <- knockerUpper.initialize: wokeUp =>
          logger.info(s"Woke up! $wokeUp")
        tripSearchWorkflowService = TripSearchWorkflowService.impl(journal, runtime)
        routes = HttpRoutes(tripSearchWorkflowService)
        _ <- runHttpServer(routes)
        _ <- IO.fromFuture(IO(actorSystem.whenTerminated))
      yield ExitCode.Success

  private def setupJournal()(using
      system: ActorSystem[Any],
  ): IO[JdbcReadJournal] =
    val journal =
      PersistenceQuery(system)
        .readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)
    IO.fromFuture(IO(SchemaUtils.createIfNotExists())).as(journal)

  private def runHttpServer(
      routes: HttpRoutes,
  )(using
      actorSystem: ActorSystem[Any],
      logger: StructuredLogger[IO],
  ): IO[Http.ServerBinding] =
    IO.fromFuture(IO(Http().newServerAt("localhost", 8989).bind(routes.routes)))
      .flatTap(binding => logger.info(s"Server online at ${binding.localAddress}"))
