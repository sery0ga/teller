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

package models.service.brand

import models.brand.BrandFee
import models.database.brand.BrandFees
import play.api.db.slick.DB
import play.api.db.slick.Config.driver.simple._
import play.api.Play.current

class BrandFeeService {

  private val fees = TableQuery[BrandFees]

  /**
   * Returns a list of fees belonged to the given brand
   * @param brandId Brand id
   */
  def findByBrand(brandId: Long): List[BrandFee] = DB.withSession {
    implicit session ⇒
      fees.filter(_.brandId === brandId).list
  }

  /**
   * Inserts the given fee into database and returns the updated fee with ID
   * @param fee Fee
   */
  def insert(fee: BrandFee): BrandFee = DB.withSession { implicit session ⇒
    val id = (fees returning fees.map(_.id)) += fee
    fee.copy(id = Some(id))
  }
}

object BrandFeeService {
  private val instance = new BrandFeeService

  def get: BrandFeeService = instance
}
