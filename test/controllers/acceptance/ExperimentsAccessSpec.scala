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

package controllers.acceptance

import _root_.integration.PlayAppSpec
import controllers.Experiments
import models.UserRole.Role._
import stubs.{ FakeRuntimeEnvironment, AccessCheckSecurity }

class ExperimentsAccessSpec extends PlayAppSpec {

  class TestExperiments extends Experiments(FakeRuntimeEnvironment)
    with AccessCheckSecurity
  val controller = new TestExperiments

  "Method 'add'" should {
    "have Viewer access right" in {
      controller.add(1L).apply(fakeGetRequest())
      controller.checkedRole must_== Some(Viewer)
    }
  }
  "Method 'create'" should {
    "have Member access right" in {
      controller.create(1L).apply(fakeGetRequest())
      controller.checkedDynamicObject must_== Some("member")
    }
  }
  "Method 'delete'" should {
    "have Member access right" in {
      controller.delete(1L, 1L).apply(fakeGetRequest())
      controller.checkedDynamicObject must_== Some("member")
    }
  }
  "Method 'deletePicture'" should {
    "have Member access right" in {
      controller.deletePicture(1L, 1L).apply(fakeGetRequest())
      controller.checkedDynamicObject must_== Some("member")
    }
  }
  "Method 'edit'" should {
    "have Viewer access right" in {
      controller.edit(1L, 1L).apply(fakeGetRequest())
      controller.checkedRole must_== Some(Viewer)
    }
  }
  "Method 'experiments'" should {
    "have Viewer access right" in {
      controller.experiments(1L).apply(fakeGetRequest())
      controller.checkedRole must_== Some(Viewer)
    }
  }
  "Method 'update'" should {
    "have Member access right" in {
      controller.update(1L, 1L).apply(fakeGetRequest())
      controller.checkedDynamicObject must_== Some("member")
    }
  }
}