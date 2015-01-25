/*
 * Happy Melly Teller
 * Copyright (C) 2013 - 2015, Happy Melly http://www.happymelly.com
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
 * If you have questions concerning this license or the applicable additional
 * terms, you may contact by email Sergey Kotlov, sergey.kotlov@happymelly.com or
 * in writing Happy Melly One, Handelsplein 37, Rotterdam, The Netherlands, 3071 PR
 */
package models

import helpers.{ OrganisationHelper, PersonHelper }
import org.joda.time.LocalDate
import org.specs2.mutable._

class MemberSpec extends Specification {

  "Member" should {
    "have a readable name if it's a person" in {
      val member = new Member(None, None, person = true, funder = true,
        BigDecimal(100), BigDecimal(100), LocalDate.now())
      member.memberObj_=(PersonHelper.one())
      member.name mustEqual "First Tester"
      member.memberObj_=(PersonHelper.two())
      member.name mustEqual "Second Tester"
    }
    "have a readable name if it's an organisation" in {
      val member = new Member(None, None, person = false, funder = true,
        BigDecimal(100), BigDecimal(100), LocalDate.now())
      member.memberObj_=(OrganisationHelper.one)
      member.name mustEqual "One"
      member.memberObj_=(OrganisationHelper.two)
      member.name mustEqual "Two"
    }
  }
}
