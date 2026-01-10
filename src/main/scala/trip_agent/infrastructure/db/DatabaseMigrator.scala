package es.eriktorr
package trip_agent.infrastructure.db

import trip_agent.TripSearchConfig.DbConfig
import trip_agent.infrastructure.data.error.HandledError

import cats.effect.IO
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateErrorResult
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension

final class DatabaseMigrator(
    dbConfig: DbConfig,
):
  def migrate: IO[Unit] =
    IO.blocking:
      val configuration =
        Flyway
          .configure()
          .dataSource(
            dbConfig.jbcUrl,
            dbConfig.username,
            dbConfig.password.value,
          )
          .failOnMissingLocations(true)
      configuration
        .getConfigurationExtension(classOf[PostgreSQLConfigurationExtension])
        .setTransactionalLock(false)
      val flyway = configuration.load()
      flyway.migrate()
    .flatMap:
        case errorResult: MigrateErrorResult =>
          IO.raiseError(DatabaseMigrator.Error.MigrationFailed(errorResult))
        case other => IO.unit

object DatabaseMigrator:
  enum Error(val message: String) extends HandledError(message):
    case MigrationFailed(errorResult: MigrateErrorResult)
        extends Error(
          s"code: ${errorResult.error.errorCode}, message: ${errorResult.error.message}, cause: ${errorResult.error.cause}",
        )
