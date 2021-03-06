package models.unit

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

import models.{ Activity, BookingEntry }
import org.joda.money.{ CurrencyUnit, Money }
import org.specs2.mutable.{ Specification, Tables }

class BookingEntrySpec extends Specification with Tables {

  "Booking entries".title

  // format: OFF
  "Booking entries calculate the correct pro rata amount by applying the source percentage to the source amount:" ^ {

    "Source" | "Percentage" | "Pro rata" |
    "1000.00" ! 100 ! "1000.00" |
    "1000.00" ! 95 ! "950.00" |
    "0.10" ! 95 ! "0.09" |> {
      (sourceAmount, percentage, result) ⇒
        val source = Money.of(CurrencyUnit.EUR, BigDecimal(sourceAmount).underlying())
        val entry = BookingEntry.blank.copy(source = source, sourcePercentage = percentage)
        val sourceProRataAmount = BigDecimal(entry.sourceProRata.getAmount)
        sourceProRataAmount.scale must be equalTo 2
        sourceProRataAmount must be equalTo BigDecimal(result)
    }
  } bt
  // format: ON

  "Booking entry" should {
    "have well-formed activity attributes" in {
      val entry = BookingEntry.blank.copy(id = Some(1L)).copy(summary = "Bill One")
      entry.objectType must_== Activity.Type.BookingEntry
      entry.identifier must_== 1
      entry.humanIdentifier must_== "Bill One"
      val entry2 = BookingEntry.blank.copy(id = Some(2L)).copy(summary = "Bill Two")
      entry2.objectType must_== Activity.Type.BookingEntry
      entry2.identifier must_== 2
      entry2.humanIdentifier must_== "Bill Two"
    }
  }
}
