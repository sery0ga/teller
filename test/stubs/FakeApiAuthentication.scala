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
 * terms, you may contact by email Sergey Kotlov, sergey.kotlov@happymelly.com
 * or in writing Happy Melly One, Handelsplein 37, Rotterdam,
 * The Netherlands, 3071 PR
 */
package stubs

import controllers.apiv2.ApiAuthentication
import models.admin.ApiToken
import play.api.mvc._

/** Stubs api authentication */
trait FakeApiAuthentication extends ApiAuthentication {

  override def TokenSecuredAction(readWrite: Boolean)(f: Request[AnyContent] ⇒ ApiToken ⇒ Result) = Action {
    implicit request ⇒
      val token = ApiToken(None, "test", "test", "test", None, readWrite)
      f(request)(token)
  }
}

/**
 * This trait is used to test that API methods have valid read/write token
 *  authentication
 */
trait FakeNoCallApiAuthentication extends ApiAuthentication {
  var readWrite: Boolean = false

  override def TokenSecuredAction(readWrite: Boolean)(f: Request[AnyContent] ⇒ ApiToken ⇒ Result) = Action {
    implicit request ⇒
      this.readWrite = readWrite
      Ok("test")
  }
}
