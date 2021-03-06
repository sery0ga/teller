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
 * If you have questions concerning this license or the applicable additional terms,
 * you may contact by email Sergey Kotlov, sergey.kotlov@happymelly.com or
 * in writing Happy Melly One, Handelsplein 37, Rotterdam, The Netherlands, 3071 PR
 */
package controllers

import models.ActiveUser
import models.UserRole.DynamicRole
import models.brand.BrandLink
import models.service.Services
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.{ JsValue, Writes, Json }
import securesocial.core.RuntimeEnvironment

class BrandLinks(environment: RuntimeEnvironment[ActiveUser])
    extends JsonController
    with Services
    with Security {

  override implicit val env: RuntimeEnvironment[ActiveUser] = environment

  implicit val brandLinkWrites = new Writes[BrandLink] {
    def writes(link: BrandLink): JsValue = {
      Json.obj(
        "brandId" -> link.brandId,
        "linkType" -> link.linkType.capitalize,
        "link" -> link.link,
        "id" -> link.id.get)
    }
  }

  /**
   * Adds new brand link if the link is valid
   *
   * @param brandId Brand identifier
   */
  def create(brandId: Long) = SecuredDynamicAction("brand", DynamicRole.Coordinator) {
    implicit request ⇒
      implicit handler ⇒ implicit user ⇒
        brandService.find(brandId) map { brand ⇒
          val form = Form(tuple("type" -> nonEmptyText, "url" -> nonEmptyText))
          form.bindFromRequest.fold(
            error ⇒ jsonBadRequest("Link cannot be empty"),
            linkData ⇒ {
              val link = BrandLink(None, brandId, linkData._1, linkData._2)
              val insertedLink = brandService.insertLink(BrandLink.updateType(link))
              jsonOk(Json.toJson(insertedLink))
            })
        } getOrElse jsonNotFound(Messages("error.brand.notFound"))
  }

  /**
   * Deletes the given brand link if the link exists and is belonged to the given
   * brand
   *
   * Brand identifier is used to check access rights
   *
   * @param brandId Brand identifier
   * @param id Link identifier
   */
  def remove(brandId: Long, id: Long) = SecuredDynamicAction("brand", DynamicRole.Coordinator) {
    implicit request ⇒
      implicit handler ⇒ implicit user ⇒
        brandService.deleteLink(brandId, id)
        jsonSuccess("ok")
  }

}
