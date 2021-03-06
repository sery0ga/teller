/*
* Happy Melly Teller
* Copyright (C) 2013 - 2015, Happy Melly http -> //www.happymelly.com
*
* This file is part of the Happy Melly Teller.
*
* Happy Melly Teller is free software ->  you can redistribute it and/or modify
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
* along with Happy Melly Teller.  If not, see <http -> //www.gnu.org/licenses/>.
*
* If you have questions concerning this license or the applicable additional
* terms, you may contact by email Sergey Kotlov, sergey.kotlov@happymelly.com
* or in writing Happy Melly One, Handelsplein 37, Rotterdam,
* The Netherlands, 3071 PR
*/
package controllers.acceptance.members

import _root_.integration.PlayAppSpec
import controllers.Members
import models.UserRole.{ DynamicRole, Role }
import org.specs2.matcher._
import stubs.{ FakeRuntimeEnvironment, AccessCheckSecurity }

import scala.concurrent.Future

/** Contains only access tests */
class AccessSpec extends PlayAppSpec {

  class TestMembers() extends Members(FakeRuntimeEnvironment)
    with AccessCheckSecurity

  val controller = new TestMembers()

  "Method 'index'" should {
    "have Viewer access rights" in {
      controller.index.apply(fakePostRequest())
      controller.checkedRole must_== Some(Role.Viewer)
    }
  }

  "Method 'add'" should {
    "have Editor access rights" in {
      controller.add().apply(fakePostRequest())
      controller.checkedRole must_== Some(Role.Editor)
    }
  }

  "Method 'edit'" should {
    "have Editor access rights" in {
      controller.edit(1L).apply(fakePostRequest())
      controller.checkedRole must_== Some(Role.Editor)
    }
  }

  "Method 'update'" should {
    "have Editor access rights" in {
      controller.update(1L).apply(fakePostRequest())
      controller.checkedRole must_== Some(Role.Editor)
    }
  }

  "Method 'addOrganisation'" should {
    "have Editor access rights" in {
      controller.addOrganisation().apply(fakePostRequest())
      controller.checkedRole must_== Some(Role.Editor)
    }
  }

  "Method 'addPerson'" should {
    "have Editor access rights" in {
      controller.addPerson().apply(fakePostRequest())
      controller.checkedRole must_== Some(Role.Editor)
    }
  }

  "Method 'addExistingOrganisation'" should {
    "have Editor access rights" in {
      controller.addExistingOrganisation().apply(fakePostRequest())
      controller.checkedRole must_== Some(Role.Editor)
    }
  }

  "Method 'addExistingPerson'" should {
    "have Editor access rights" in {
      controller.addExistingPerson().apply(fakePostRequest())
      controller.checkedRole must_== Some(Role.Editor)
    }
  }

  "Method 'delete'" should {
    "have Editor access rights" in {
      controller.delete(1L).apply(fakePostRequest())
      controller.checkedRole must_== Some(Role.Editor)
    }
  }

  "Method 'updateReason'" should {
    "have Editor access rights" in {
      controller.updateReason(1L).apply(fakePostRequest())
      controller.checkedDynamicObject must_== Some("person")
      controller.checkedDynamicLevel must_== Some("edit")
    }
  }

}

