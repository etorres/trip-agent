package es.eriktorr
package trip_agent.application

import cats.effect.IO

trait MailSender:
  def sendEmail(
      to: String,
      subject: String,
      content: String,
  ): IO[Unit]

object MailSender:
  def impl(
      from: String,
  ): MailSender =
    (
        to: String,
        subject: String,
        content: String,
    ) => IO.println(s"""From: $from
                       |To: $to
                       |Subject: $subject
                       |$content""".stripMargin)
