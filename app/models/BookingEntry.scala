/*
 * Happy Melly Teller
 * Copyright (C) 2013 - 2014, Happy Melly http://www.happymelly.com
 *
 * This file is part of the Happy Melly Teller.
 *
 * Happy Melly Teller is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Happy Melly Teller is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Happy Melly Teller.  If not, see <http://www.gnu.org/licenses/>.
 *
 * If you have questions concerning this license or the applicable additional terms, you may contact
 * by email Sergey Kotlov, sergey.kotlov@happymelly.com or
 * in writing Happy Melly One, Handelsplein 37, Rotterdam, The Netherlands, 3071 PR
 */

package models

import models.database.PortableJodaSupport._
import java.math.RoundingMode
import java.net.URLEncoder
import models.JodaMoney._
import models.admin.TransactionType
import models.database._
import models.service.{ BrandService, PersonService }
import org.joda.time.{ Days, DateTime, LocalDate }
import org.joda.money.{ CurrencyUnit, Money }
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick.DB
import services.CurrencyConverter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import services.S3Bucket
import services.CurrencyConverter.NoExchangeRateException
import scala.Some

/**
 * A financial (accounting) bookkeeping entry, which represents money owed from one account to another.
 */
case class BookingEntry(
    id: Option[Long],
    ownerId: Long,
    bookingDate: LocalDate,
    bookingNumber: Option[Int],
    summary: String,

    source: Money,
    sourcePercentage: Int,
    fromId: Long,
    fromAmount: Money,
    toId: Long,
    toAmount: Money,

    brandId: Option[Long],
    reference: Option[String],
    referenceDate: LocalDate,
    description: Option[String] = None,
    url: Option[String] = None,
    transactionTypeId: Option[Long] = None,
    attachmentKey: Option[String] = None,

    created: DateTime = DateTime.now()) extends ActivityRecorder {

  lazy val from = Account.find(fromId).get

  lazy val to = Account.find(toId).get

  lazy val owner = PersonService.get.find(ownerId).get

  lazy val brand = brandId.flatMap(BrandService.get.find(_))

  val owes = source.isPositiveOrZero

  val owesText = if (owes) "owes" else "owed by"

  lazy val transactionType = transactionTypeId.flatMap(TransactionType.find)

  lazy val editable = from.active && to.active

  lazy val participants = from.participants ++ to.participants

  /**
   * Returns identifier of the object
   */
  def identifier: Long = id.getOrElse(0)

  /**
   * Returns string identifier which can be understood by human
   */
  def humanIdentifier: String = summary

  /**
   * Returns type of this object
   */
  def objectType: String = Activity.Type.BookingEntry

  /**
   * Checks if the given user has permission to edit this booking entry.
   */
  def editableBy(user: UserAccount) = {
    editable && (user.admin || user.personId == ownerId || from.editableBy(user) || to.editableBy(user))
  }

  def insert: BookingEntry = DB.withSession { implicit session: Session ⇒
    val nextBookingNumber = Some(BookingEntry.nextBookingNumber)
    val entries = TableQuery[BookingEntries]
    val id = (entries returning entries.map(_.id)) += this.copy(bookingNumber = nextBookingNumber)
    this.copy(id = Some(id), bookingNumber = nextBookingNumber)
  }

  /**
   * Returns the source amount with the percentage applied, used to calculate from and to amounts.
   */
  def sourceProRata: Money = {
    val percentage = BigDecimal(sourcePercentage) / BigDecimal(BookingEntry.DefaultSourcePercentage)
    source.multipliedBy(percentage.underlying(), RoundingMode.DOWN)
  }

  /**
   * Creates a copy of this `BookingEntry` with the value of `fromAmount` and `toAmount` set by converting
   * `source` to the currency for the `from` and `to` accounts respectively, using today’s exchange rate,
   * and applying the source percentage.
   *
   * Returns a `Future` because WS calls are potentially involved.
   */
  def withSourceConverted: Future[BookingEntry] = {
    val future = for {
      fromAmountConverted ← CurrencyConverter.convert(sourceProRata, from.currency)
      toAmountConverted ← CurrencyConverter.convert(sourceProRata, to.currency)
    } yield copy(fromAmount = fromAmountConverted, toAmount = toAmountConverted)

    // Preserve the error for the controller to handle.
    future.recover {
      case e ⇒ throw e
    }
  }

  /**
   * Creates a signed URL for the file attachment, valid for 1 hour.
   */
  def attachmentUrl: Option[String] = attachmentKey.map { key ⇒
    S3Bucket.url(URLEncoder.encode(key, "UTF-8"), Days.ONE.toStandardSeconds.getSeconds)
  }

  /**
   * Returns the filename for the file attachment based on its key.
   */
  def attachmentFilename = attachmentKey.map { _.split("/").last }

}

/**
 * A view on a booking entry for the overview page.
 */
case class BookingEntrySummary(
  bookingNumber: Int,
  bookingDate: LocalDate,

  source: Money,
  sourcePercentage: Int,
  from: String,
  fromAmount: Money,
  owes: Boolean,
  to: String,
  toAmount: Money,

  brandId: Option[Long],
  brandName: Option[String],
  summary: String,

  fromId: Long,
  toId: Long)

object BookingEntry {

  val DefaultSourcePercentage = 100

  /**
   * A string representation of a change to a field value.
   */
  case class FieldChange(label: String, oldValue: String, newValue: String) {
    override def toString = s"$label: $newValue (was: $oldValue)"

    def printable(): (String, String, String) = (label, newValue, oldValue)
  }

  /**
   * Compares two booking entires and returns a list of changes.
   * @param was The booking entry with ‘old’ values.
   * @param now The booking entry with ‘new’ values.
   */
  def compare(was: BookingEntry, now: BookingEntry): List[FieldChange] = {
    import templates.Formatters._
    val changes = List(
      FieldChange("Summary", was.summary, now.summary),
      FieldChange("Source amount", was.source.abs.formatText, now.source.abs.formatText),
      FieldChange("Source percentage", was.sourcePercentage.toString, now.sourcePercentage.toString),
      FieldChange("From amount", was.fromAmount.abs.formatText, now.fromAmount.abs.formatText),
      FieldChange("Transaction direction", was.owesText, now.owesText),
      FieldChange("To amount", was.toAmount.abs.formatText, now.toAmount.abs.formatText),
      FieldChange("Brand", was.brand.map(_.code).getOrElse(""), now.brand.map(_.code).getOrElse("")),
      FieldChange("Reference", was.reference.getOrElse(""), now.reference.getOrElse("")),
      FieldChange("Reference date", was.referenceDate.format, now.referenceDate.format),
      FieldChange("Transaction type", was.transactionType.map(_.name).getOrElse(""), now.transactionType.map(_.name).getOrElse("")),
      FieldChange("Attachment", was.attachmentFilename.getOrElse(""), now.attachmentFilename.getOrElse("")))

    changes.filter(change ⇒ change.oldValue != change.newValue)
  }

  def blank = BookingEntry(None, 0L, LocalDate.now, None, "", Money.of(CurrencyUnit.EUR, 0f), DefaultSourcePercentage,
    0, Money.zero(CurrencyUnit.EUR), 0, Money.zero(CurrencyUnit.EUR), None, None, LocalDate.now)

  def findByBookingNumber(bookingNumber: Int): Option[BookingEntry] = DB.withSession {
    implicit session ⇒
      Entries.filtered.filter(_.bookingNumber === bookingNumber).firstOption
  }

  // Define a query that does left outer joins on the to/from accounts’ optional person/organisation records.
  // For now, only the names are retrieved; if the web page requires hyperlinks, then a richer structure is needed.
  lazy val bookingEntriesQuery = for {
    (entry, brand) ← Entries.filtered leftJoin TableQuery[Brands] on (_.brandId === _.id)
    ((fromAccount, fromPerson), fromOrganisation) ← TableQuery[Accounts] leftJoin
      TableQuery[People] on (_.personId === _.id) leftJoin
      TableQuery[Organisations] on (_._1.organisationId === _.id)
    if fromAccount.id === entry.fromId
    ((toAccount, toPerson), toOrganisation) ← TableQuery[Accounts] leftJoin
      TableQuery[People] on (_.personId === _.id) leftJoin
      TableQuery[Organisations] on (_._1.organisationId === _.id)
    if toAccount.id === entry.toId
  } yield (fromAccount.id, toAccount.id, entry.created, entry.bookingNumber, entry.bookingDate,
    entry.sourceCurrency -> entry.sourceAmount, entry.sourcePercentage,
    fromPerson.firstName.?, fromPerson.lastName.?, fromOrganisation.name.?, entry.fromCurrency -> entry.fromAmount,
    toPerson.firstName.?, toPerson.lastName.?, toOrganisation.name.?, entry.toCurrency -> entry.toAmount,
    brand.id.?, brand.name.?, entry.summary)

  type BookingEntriesQueryResult = (Long, Long, DateTime, Int, LocalDate, (String, BigDecimal), Int, Option[String], Option[String], Option[String], (String, BigDecimal), Option[String], Option[String], Option[String], (String, BigDecimal), Option[Long], Option[String], String)

  val mapBookingEntryResult: (BookingEntriesQueryResult ⇒ BookingEntrySummary) = {
    case (fromId, toId, created, number, date, source, sourcePercentage, fromPersonFirstName, fromPersonLastName, fromOrganisation,
      fromAmount, toPersonFirstName, toPersonLastName, toOrganisation, toAmount, brandId, brandName, summary) ⇒ {
      val from = Account.accountHolderName(fromPersonFirstName, fromPersonLastName, fromOrganisation)
      val to = Account.accountHolderName(toPersonFirstName, toPersonLastName, toOrganisation)
      val owes = source.isPositiveOrZero
      BookingEntrySummary(number, date, source, sourcePercentage, from, fromAmount, owes, to, toAmount, brandId, brandName,
        summary, fromId, toId)
    }
  }

  /**
   * Soft-deletes a booking entry by marking it as deleted.
   */
  def delete(id: Long): Unit = DB.withSession { implicit session ⇒
    TableQuery[BookingEntries].filter(_.id === id).map(_.deleted).update(true)
  }

  /**
   * Returns a list of entries in reverse chronological order of date created.
   */
  def findAll: List[BookingEntrySummary] = DB.withSession { implicit session: Session ⇒
    bookingEntriesQuery.sortBy(_._4.desc).mapResult(mapBookingEntryResult).list
  }

  /**
   * Returns a list of entries for the given account, in reverse chronological order of date created.
   */
  def findByAccountId(accountId: Long, from: Option[LocalDate], to: Option[LocalDate]): List[BookingEntrySummary] = DB.withSession { implicit session: Session ⇒
    val baseQuery = bookingEntriesQuery.filter(row ⇒ row._1 === accountId || row._2 === accountId)

    val fromQuery = from.map { fromDate ⇒
      baseQuery.filter(row ⇒ row._5 >= fromDate)
    }.getOrElse(baseQuery)

    val toQuery = to.map { toDate ⇒
      fromQuery.filter(row ⇒ row._5 <= toDate)
    }.getOrElse(fromQuery)

    toQuery.sortBy(_._4.desc).mapResult(mapBookingEntryResult).list
  }

  private def nextBookingNumber: Int = DB.withSession { implicit session ⇒
    TableQuery[BookingEntries].map(_.bookingNumber).max.run.map(_ + 1).getOrElse(1001)
  }

  /**
   * Updates a booking entry without changing its ID, owner, booking number, booking date or date created.
   */
  def update(e: BookingEntry): Unit = DB.withSession { implicit session ⇒
    e.id.map { id ⇒
      val sourceAmount: BigDecimal = e.source.getAmount
      val toAmount: BigDecimal = e.toAmount.getAmount
      val fromAmount: BigDecimal = e.fromAmount.getAmount
      val updateTuple = (e.summary, e.source.getCurrencyUnit.getCode, sourceAmount, e.sourcePercentage, e.fromId,
        e.fromAmount.getCurrencyUnit.getCode, fromAmount, e.toId, e.toAmount.getCurrencyUnit.getCode, toAmount,
        e.brandId, e.reference, e.referenceDate, e.description, e.url, e.attachmentKey, e.transactionTypeId)
      val updateQuery = TableQuery[BookingEntries].filter(_.id === id).map(_.forUpdate)
      updateQuery.update(updateTuple)
    }
  }
}
