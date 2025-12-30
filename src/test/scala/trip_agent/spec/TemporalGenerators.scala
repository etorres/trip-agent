package es.eriktorr
package trip_agent.spec

import cats.collections.Range
import com.fortysevendeg.scalacheck.datetime.GenDateTime.genDateTimeWithinRange
import com.fortysevendeg.scalacheck.datetime.YearRange
import com.fortysevendeg.scalacheck.datetime.instances.jdk8.jdk8ForDuration
import com.fortysevendeg.scalacheck.datetime.jdk8.ArbitraryJdk8.arbZonedDateTimeJdk8
import com.fortysevendeg.scalacheck.datetime.jdk8.granularity.seconds
import org.scalacheck.Gen

import java.time.*
import java.time.temporal.ChronoUnit.SECONDS

object TemporalGenerators:
  private val yearRange: YearRange = YearRange.between(1990, 2060)

  private val minLocalDate: LocalDate = LocalDate.of(yearRange.min, Month.JANUARY, 1)
  private val maxLocalDate: LocalDate = LocalDate.of(yearRange.max, Month.DECEMBER, 31)

  private val zoneId = ZoneOffset.UTC

  val zonedDateTimeGen: Gen[ZonedDateTime] =
    arbZonedDateTimeJdk8(using granularity = seconds, yearRange = yearRange).arbitrary

  def zonedDateTimeAfter(moment: ZonedDateTime): Gen[ZonedDateTime] =
    withinZonedDateTimeRange(
      Range(
        moment.plusSeconds(1L),
        maxLocalDate.atTime(LocalTime.MAX).atZone(zoneId),
      ),
    )

  def zonedDateTimeBefore(moment: ZonedDateTime): Gen[ZonedDateTime] =
    withinZonedDateTimeRange(
      Range(
        minLocalDate.atTime(LocalTime.MIN).atZone(zoneId),
        moment.minusSeconds(1L),
      ),
    )

  def withinZonedDateTimeRange(range: Range[ZonedDateTime]): Gen[ZonedDateTime] =
    genDateTimeWithinRange(
      range.start,
      Duration.ofSeconds(SECONDS.between(range.start, range.end)),
    )(using scDateTime = jdk8ForDuration, granularity = seconds)
