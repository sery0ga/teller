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
package controllers.acceptance

import controllers.Urls
import integration.PlayAppSpec
import play.api.libs.json.JsObject
import stubs.{ FakeRuntimeEnvironment, FakeUserIdentity, FakeSecurity }

class UrlsSpec extends PlayAppSpec {

  override def is = s2"""

  When a webpage doesn't exist
    'invalid' should be returned  $e1

  When a webpage exists
    'valid' should be returned    $e2
  """

  class TestUrls extends Urls(FakeRuntimeEnvironment) with FakeSecurity

  val controller = new TestUrls

  def e1 = {
    val url = "http://notexisting312312398098dsalfjda.com"
    val result = controller.validate(url).apply(fakePostRequest())

    status(result) must equalTo(OK)
    val data = contentAsJson(result).asInstanceOf[JsObject]
    (data \ "result").as[String] must_== "invalid"
  }

  def e2 = {
    val url = "http://t.co"
    val result = controller.validate(url).apply(fakePostRequest())

    status(result) must equalTo(OK)
    val data = contentAsJson(result).asInstanceOf[JsObject]
    (data \ "result").as[String] must_== "valid"
  }

}