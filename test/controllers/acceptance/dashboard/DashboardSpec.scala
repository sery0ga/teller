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
package controllers.acceptance.dashboard

import _root_.integration.PlayAppSpec
import controllers.{ Dashboard, Security }
import helpers.{ BrandHelper, EvaluationHelper, EventHelper, PersonHelper }
import models._
import models.service._
import org.joda.money.CurrencyUnit._
import org.joda.money.Money
import org.joda.time.{ DateTime, LocalDate }
import org.scalamock.specs2.{ IsolatedMockFactory, MockContext }
import play.api.mvc.Result
import stubs._

import scala.collection.mutable
import scala.concurrent.Future

class TestDashboard() extends Dashboard(FakeRuntimeEnvironment)
  with FakeSecurity with FakeServices

class DashboardSpec extends PlayAppSpec with IsolatedMockFactory {

  override def is = s2"""

    On facilitator's dashboard there should be
      three nearest future events               $e7
      two last previous events                  $e11
      10 latest evaluations                     $e8

    A list of facilitators with expiring licenses should be
      visible if a person is a brand coordinator $e9
      not be visible to Viewer                               $e10
  """

  val controller = new TestDashboard()
  val brandService = mock[BrandService]
  controller.brandService_=(brandService)
  val eventService = mock[EventService]
  controller.eventService_=(eventService)
  val licenseService = mock[LicenseService]
  controller.licenseService_=(licenseService)
  val evaluationService = mock[EvaluationService]
  controller.evaluationService_=(evaluationService)

  def e7 = {
    val events = List(EventHelper.future(1, 1), EventHelper.future(2, 2),
      EventHelper.future(3, 3), EventHelper.future(4, 4),
      EventHelper.past(5, 3), EventHelper.past(6, 2))
    (brandService.findByCoordinator _) expects 1L returning List()
    (eventService.findByFacilitator _) expects (1L, None, *, *, *) returning events
    (evaluationService.findByEventsWithParticipants _) expects List(6L, 5L) returning List()

    val result = controller.index().apply(fakeGetRequest())
    status(result) must equalTo(OK)
    contentAsString(result) must contain("Upcoming events")
    contentAsString(result) must contain("/event/1")
    contentAsString(result) must contain("/event/2")
    contentAsString(result) must contain("/event/3")
    contentAsString(result) must not contain "/event/4"
  }

  def e8 = {
    val evalStatus = EvaluationStatus.Pending
    val evaluations: List[(Event, Person, Evaluation)] = List(
      (EventHelper.one, PersonHelper.one(),
        EvaluationHelper.make(Some(13L), 1L, 1L, evalStatus, 8, DateTime.now().minusHours(13))),
      (EventHelper.one, PersonHelper.two(),
        EvaluationHelper.make(Some(2L), 1L, 2L, evalStatus, 8, DateTime.now().minusHours(12))),
      (EventHelper.one, PersonHelper.fast(3, "Three", "Lad"),
        EvaluationHelper.make(Some(3L), 1L, 3L, evalStatus, 8, DateTime.now().minusHours(11))),
      (EventHelper.one, PersonHelper.fast(4, "Four", "Girl"),
        EvaluationHelper.make(Some(4L), 1L, 4L, evalStatus, 8, DateTime.now().minusHours(10))),
      (EventHelper.one, PersonHelper.fast(5, "Five", "Lad"),
        EvaluationHelper.make(Some(5L), 1L, 5L, evalStatus, 8, DateTime.now().minusHours(9))),
      (EventHelper.one, PersonHelper.fast(6, "Six", "Girl"),
        EvaluationHelper.make(Some(6L), 1L, 6L, evalStatus, 8, DateTime.now().minusHours(8))),
      (EventHelper.one, PersonHelper.fast(7, "Seven", "Lad"),
        EvaluationHelper.make(Some(7L), 1L, 7L, evalStatus, 8, DateTime.now().minusHours(7))),
      (EventHelper.one, PersonHelper.fast(8, "Eight", "Girl"),
        EvaluationHelper.make(Some(8L), 1L, 8L, evalStatus, 8, DateTime.now().minusHours(6))),
      (EventHelper.one, PersonHelper.fast(9, "Nine", "Lad"),
        EvaluationHelper.make(Some(9L), 1L, 9L, evalStatus, 8, DateTime.now().minusHours(5))),
      (EventHelper.one, PersonHelper.fast(10, "Ten", "Girl"),
        EvaluationHelper.make(Some(10L), 1L, 10L, evalStatus, 8, DateTime.now().minusHours(4))),
      (EventHelper.one, PersonHelper.fast(11, "Eleven", "Lad"),
        EvaluationHelper.make(Some(11L), 1L, 11L, evalStatus, 8, DateTime.now().minusHours(3))))
    (brandService.findByCoordinator _) expects 1L returning List()
    (evaluationService.findByEventsWithParticipants _) expects List() returning evaluations
    (eventService.findByFacilitator _) expects (1L, None, *, *, *) returning List()

    val result = controller.index().apply(fakeGetRequest())
    status(result) must equalTo(OK)
    contentAsString(result) must contain("Latest evaluations")
    contentAsString(result) must not contain "/evaluation/13"
    contentAsString(result) must contain("/evaluation/2")
    contentAsString(result) must contain("/evaluation/3")
    contentAsString(result) must contain("/evaluation/4")
    contentAsString(result) must contain("/evaluation/5")
    contentAsString(result) must contain("/evaluation/6")
    contentAsString(result) must contain("/evaluation/7")
    contentAsString(result) must contain("/evaluation/8")
    contentAsString(result) must contain("/evaluation/9")
    contentAsString(result) must contain("/evaluation/10")
    contentAsString(result) must contain("/evaluation/11")
  }

  def e9 = {
    val facilitators = Map(1L -> PersonHelper.one(),
      2L -> PersonHelper.two())
    val now = LocalDate.now()
    val licenses = mutable.MutableList[LicenseLicenseeView]()
    Seq(
      (1L, now.withDayOfMonth(1), now.dayOfMonth().withMinimumValue()),
      (2L, now.minusYears(1), now.dayOfMonth().withMaximumValue())).foreach {
        case (licenseeId, start, end) ⇒ {
          val license = new License(None, licenseeId, 1L,
            "1", LocalDate.now().minusYears(1),
            start, end, true, Money.of(EUR, 100), Some(Money.of(EUR, 100)))
          licenses += LicenseLicenseeView(license, facilitators.get(licenseeId).get)
        }
      }
    (brandService.findByCoordinator _) expects 1L returning List(BrandHelper.one)
    (eventService.findByFacilitator _) expects (1L, None, *, *, *) returning List()
    (evaluationService.findByEventsWithParticipants _) expects List() returning List()
    (licenseService.expiring _) expects (List(1L)) returning licenses.toList

    val result = controller.index().apply(fakeGetRequest())
    status(result) must equalTo(OK)
    val title = "Expiring licenses"
    contentAsString(result) must contain(title)
    contentAsString(result) must contain("/person/1")
    contentAsString(result) must contain("/person/2")
    contentAsString(result) must contain("EUR 100")
    contentAsString(result) must contain("First Tester")
    contentAsString(result) must contain("Second Tester")
    contentAsString(result) must contain(now.dayOfMonth().withMinimumValue().toString)
    contentAsString(result) must contain(now.dayOfMonth().withMaximumValue().toString)
    contentAsString(result) must not contain "/person/4"
    contentAsString(result) must not contain "/person/5"
  }

  def e10 = {
    (brandService.findByCoordinator _) expects 1L returning List()
    (eventService.findByFacilitator _) expects (1L, None, *, *, *) returning List()
    (evaluationService.findByEventsWithParticipants _) expects List() returning List()

    val result = controller.index().apply(fakeGetRequest())
    status(result) must equalTo(OK)
    val title = "Expiring licenses in " + month(LocalDate.now().getMonthOfYear)
    contentAsString(result) must not contain title
  }

  def e11 = {
    val events = List(EventHelper.future(1, 1), EventHelper.future(2, 2),
      EventHelper.past(3, 3), EventHelper.past(4, 2),
      EventHelper.past(5, 3), EventHelper.past(6, 1))
    (brandService.findByCoordinator _) expects 1L returning List()
    (eventService.findByFacilitator _) expects (1L, None, *, *, *) returning events
    (evaluationService.findByEventsWithParticipants _) expects List(6L, 4L, 3L, 5L) returning List()

    val result = controller.index().apply(fakeGetRequest())
    status(result) must equalTo(OK)
    contentAsString(result) must contain("Last past events")
    contentAsString(result) must contain("/event/6")
    contentAsString(result) must contain("/event/4")
  }

  /**
   * Returns name of month by its index
   * @param index Index of month
   */
  private def month(index: Int): String = {
    val months = Map(1 -> "January", 2 -> "February", 3 -> "March", 4 -> "April",
      5 -> "May", 6 -> "June", 7 -> "July", 8 -> "August", 9 -> "September",
      10 -> "October", 11 -> "November", 12 -> "December")
    months.getOrElse(index, "")
  }
}
