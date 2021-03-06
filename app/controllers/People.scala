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

package controllers

import controllers.Forms._
import models.UserRole.Role._
import models._
import models.payment.{ GatewayWrapper, PaymentException, RequestException }
import models.service.Services
import org.joda.time.DateTime
import play.api.{ Logger, Play }
import play.api.Play.current
import play.api.data.Forms._
import play.api.data.validation.Constraints
import play.api.data.{ Form, FormError }
import play.api.i18n.Messages
import securesocial.core.RuntimeEnvironment
import scala.language.postfixOps
import scala.collection.mutable
import services.integrations.Integrations

class People(environment: RuntimeEnvironment[ActiveUser])
    extends JsonController
    with Security
    with Services
    with Integrations
    with Files
    with Activities {

  override implicit val env: RuntimeEnvironment[ActiveUser] = environment

  val contentType = "image/jpeg"

  /**
   * Form target for toggling whether a person is active
   *
   * @param id Person identifier
   */
  def activation(id: Long) = SecuredRestrictedAction(Editor) { implicit request ⇒
    implicit handler ⇒ implicit user ⇒

      personService.find(id).map { person ⇒
        Form("active" -> boolean).bindFromRequest.fold(
          form ⇒ BadRequest("invalid form data"),
          active ⇒ {
            Person.activate(id, active)
            val log = if (active)
              activity(person, user.person).activated.insert()
            else
              activity(person, user.person).deactivated.insert()
            Redirect(routes.People.details(id)).flashing("success" -> log.toString)
          })
      } getOrElse {
        Redirect(routes.People.index()).flashing(
          "error" -> Messages("error.notFound", Messages("models.Person")))
      }
  }

  /**
   * Render a Create page
   */
  def add = SecuredRestrictedAction(Editor) { implicit request ⇒
    implicit handler ⇒ implicit user ⇒
      Ok(views.html.v2.person.form(user, None, People.personForm(user.name)))
  }

  /**
   * Assign a person to an organisation
   */
  def addRelationship() = SecuredDynamicAction("person", "edit") { implicit request ⇒
    implicit handler ⇒ implicit user ⇒

      val relationshipForm = Form(tuple("page" -> text,
        "personId" -> longNumber,
        "organisationId" -> longNumber))

      relationshipForm.bindFromRequest.fold(
        errors ⇒ BadRequest("organisationId missing"),
        {
          case (page, personId, organisationId) ⇒
            personService.find(personId).map { person ⇒
              orgService.find(organisationId).map { organisation ⇒
                person.addRelation(organisationId)

                val log = activity(person, user.person,
                  Some(organisation)).connected.insert()
                // Redirect to the page we came from - either the person or organisation details page.
                val action = if (page == "person")
                  routes.People.details(personId).url
                else
                  routes.Organisations.details(organisationId).url
                Redirect(action).flashing("success" -> log.toString)
              }.getOrElse(NotFound)
            }.getOrElse(NotFound)
        })
  }

  /**
   * Create form submits to this action.
   */
  def create = SecuredRestrictedAction(Editor) { implicit request ⇒
    implicit handler ⇒ implicit user ⇒

      People.personForm(user.name).bindFromRequest.fold(
        formWithErrors ⇒
          BadRequest(views.html.v2.person.form(user, None, formWithErrors)),
        person ⇒ {
          val updatedPerson = person.insert
          val log = activity(updatedPerson, user.person).created.insert()
          Redirect(routes.People.index()).flashing("success" -> log.toString)
        })
  }

  /**
   * Delete a person
   *
   * @param id Person identifier
   */
  def delete(id: Long) = SecuredDynamicAction("person", "delete") { implicit request ⇒
    implicit handler ⇒ implicit user ⇒

      personService.find(id).map { person ⇒
        if (!person.deletable) {
          Redirect(routes.People.index()).flashing("error" -> Messages("error.person.nonDeletable"))
        } else {
          personService.delete(id)
          val log = activity(person, user.person).deleted.insert()
          Redirect(routes.People.index()).flashing("success" -> log.toString)
        }
      }.getOrElse(NotFound)
  }

  /**
   * Delete a relationthip of a person and an organisation
   *
   * @param page Page identifier where the action was requested from
   * @param personId Person identifier
   * @param organisationId Org identifier
   */
  def deleteRelationship(page: String,
    personId: Long,
    organisationId: Long) = SecuredDynamicAction("person", "edit") {
    implicit request ⇒
      implicit handler ⇒ implicit user ⇒

        personService.find(personId).map { person ⇒
          orgService.find(organisationId).map { organisation ⇒
            person.deleteRelation(organisationId)
            val log = activity(person, user.person,
              Some(organisation)).disconnected.insert()
            // Redirect to the page we came from - either the person or
            // organisation details page.
            val action = if (page == "person")
              routes.People.details(personId).url
            else
              routes.Organisations.details(organisationId).url
            Redirect(action).flashing("success" -> log.toString)
          }
        }.flatten.getOrElse(NotFound)
  }

  /**
   * Render Details page
   *
   * @param id Person identifier
   */
  def details(id: Long) = SecuredRestrictedAction(Viewer) { implicit request ⇒
    implicit handler ⇒ implicit user ⇒
      personService.find(id) map { person ⇒
        val licenses = licenseService.licenses(id)
        val facilitator = licenses.nonEmpty
        val memberships = person.organisations
        val otherOrganisations = orgService.findActive.filterNot(organisation ⇒
          memberships.contains(organisation))
        val accountRole = if (user.account.editor)
          userAccountService.findRole(id) else None
        val duplicated = if (user.account.editor)
          userAccountService.findDuplicateIdentity(person)
        else None
        Ok(views.html.v2.person.details(user, person,
          memberships, otherOrganisations,
          facilitator, accountRole, duplicated))
      } getOrElse {
        Redirect(routes.People.index()).flashing(
          "error" -> Messages("error.notFound", Messages("models.Person")))
      }
  }

  /**
   * Render an Edit page
   *
   * @param id Person identifier
   */
  def edit(id: Long) = SecuredDynamicAction("person", "edit") { implicit request ⇒
    implicit handler ⇒ implicit user ⇒

      personService.find(id).map { person ⇒
        Ok(views.html.v2.person.form(user, Some(id),
          People.personForm(user.name).fill(person)))
      }.getOrElse(NotFound)
  }

  /**
   * Edit form submits to this action
   *
   * @param id Person identifier
   */
  def update(id: Long) = SecuredDynamicAction("person", "edit") {
    implicit request ⇒
      implicit handler ⇒ implicit user ⇒
        personService.find(id) map { oldPerson ⇒
          People.personForm(user.name).bindFromRequest.fold(
            formWithErrors ⇒
              BadRequest(views.html.v2.person.form(user, Some(id), formWithErrors)),
            person ⇒ {
              checkDuplication(person, id, user.name) map { form ⇒
                BadRequest(views.html.v2.person.form(user, Some(id), form))
              } getOrElse {
                val updatedPerson = person
                  .copy(id = Some(id), active = oldPerson.active)
                  .copy(photo = oldPerson.photo, customerId = oldPerson.customerId)
                  .copy(addressId = oldPerson.addressId)
                personService.member(id) foreach { x ⇒
                  val msg = composeSocialNotification(oldPerson, updatedPerson)
                  msg foreach { slack.send(_) }
                }
                updatedPerson.update
                val log = activity(updatedPerson, user.person).updated.insert()
                Redirect(routes.People.details(id)).flashing(
                  "success" -> log.toString)
              }
            })
        } getOrElse NotFound
  }

  /**
   * Renders tab for the given person
   * @param id Person or Member identifier
   * @param tab Tab identifier
   */
  def renderTabs(id: Long, tab: String) = SecuredRestrictedAction(Viewer) {
    implicit request ⇒
      implicit handler ⇒ implicit user ⇒
        tab match {
          case "contributions" ⇒
            val contributions = contributionService.contributions(id, isPerson = true)
            Ok(views.html.v2.element.contributions("person", contributions))
          case "experience" ⇒
            personService.find(id) map { person ⇒
              val experience = retrieveByBrandStatistics(id)
              val endorsements = personService.endorsements(id).map {x =>
                (x, experience.find(_._1 == x.brandId).map(_._2).getOrElse(""))
              }
              val materials = personService.materials(id).sortBy(_.linkType)
              Ok(views.html.v2.person.tabs.experience(person, experience,
                endorsements, materials))
            } getOrElse NotFound("Person not found")
          case "facilitation" ⇒
            personService.find(id) map { person ⇒
              val licenses = licenseService.licenses(id)
              val facilitation = facilitatorService.findByPerson(id)
              val facilitatorData = licenses
                .map(x ⇒ (x, facilitation.find(_.brandId == x.license.brandId).get.rating))
              Ok(views.html.v2.person.tabs.facilitation(person, facilitatorData))
            } getOrElse NotFound("Person not found")
          case "membership" ⇒
            personService.find(id) map { person ⇒
              person.member map { v ⇒
                val payments = paymentRecordService.findByPerson(id)
                Ok(views.html.v2.person.tabs.membership(user, person, payments))
              } getOrElse Ok("Person is not a member")
            } getOrElse NotFound("Person not found")
          case _ ⇒ Ok("")
        }
  }


  /**
   * Render a list of people in the network
   *
   * @return
   */
  def index = SecuredRestrictedAction(Viewer) { implicit request ⇒
    implicit handler ⇒ implicit user ⇒
      val people = models.Person.findAll
      Ok(views.html.v2.person.index(user, people))
  }

  /**
   * Cancels a subscription for yearly-renewing membership
   * @param id Person id
   */
  def cancel(id: Long) = SecuredDynamicAction("person", "edit") { implicit request ⇒
    implicit handler ⇒ implicit user ⇒
      val url = routes.People.details(id).url + "#membership"
      personService.find(id) map { person ⇒
        person.member map { m ⇒
          if (m.renewal) {
            val key = Play.configuration.getString("stripe.secret_key").get
            val gateway = new GatewayWrapper(key)
            try {
              gateway.cancel(person.customerId.get)
              m.copy(renewal = false).update
            } catch {
              case e: PaymentException ⇒
                Redirect(url).flashing("error" -> Messages(e.msg))
              case e: RequestException ⇒
                e.log.foreach(Logger.error(_))
                Redirect(url).flashing("error" -> Messages(e.getMessage))
            }
            Redirect(url).
              flashing("success" -> "Subscription was successfully canceled")
          } else {
            Redirect(url).
              flashing("error" -> Messages("error.membership.noSubscription"))
          }
        } getOrElse {
          Redirect(url).
            flashing("error" -> Messages("error.membership.noSubscription"))
        }
      } getOrElse NotFound
  }

  /**
   * Returns form with erros if a person with identical social networks exists
   *
   * @param person Person object with incomplete social profile
   * @param id Identifier of a person which is updated
   * @param editorName Name of a user who adds changes
   */
  protected def checkDuplication(person: Person, id: Long, editorName: String): Option[Form[Person]] = {
    val base = person.socialProfile.copy(objectId = id, objectType = ProfileType.Person)
    socialProfileService.findDuplicate(base) map { duplicate ⇒
      var form = People.personForm(editorName).fill(person)
      compareSocialProfiles(duplicate, base).
        foreach(err ⇒ form = form.withError(err))
      Some(form)
    } getOrElse None
  }

  /**
   * Compares social profiles and returns a list of errors for a form
   * @param left Social profile object
   * @param right Social profile object
   */
  protected def compareSocialProfiles(left: SocialProfile,
    right: SocialProfile): List[FormError] = {

    val list = mutable.MutableList[FormError]()
    val msg = Messages("error.socialProfile.exist")
    if (left.twitterHandle.nonEmpty && left.twitterHandle == right.twitterHandle) {
      list += FormError("profile.twitterHandle", msg)
    }
    if (left.facebookUrl.nonEmpty && left.facebookUrl == right.facebookUrl) {
      list += FormError("profile.facebookUrl", msg)
    }
    if (left.linkedInUrl.nonEmpty && left.linkedInUrl == right.linkedInUrl) {
      list += FormError("profile.linkedInUrl", msg)
    }
    if (left.googlePlusUrl.nonEmpty && left.googlePlusUrl == right.googlePlusUrl) {
      list += FormError("profile.googlePlusUrl", msg)
    }
    list.toList
  }

  /**
   * Compares social profiles and returns a list of errors for a form
   *
   * @param existing Person
   * @param updated Updated person
   */
  protected def composeSocialNotification(existing: Person,
    updated: Person): Option[String] = {
    val updatedProfile = updated.socialProfile
    val oldProfile = existing.socialProfile
    val notifications = List(
      composeNotification("twitter", oldProfile.twitterHandle, updatedProfile.twitterHandle),
      composeNotification("facebook", oldProfile.facebookUrl, updatedProfile.facebookUrl),
      composeNotification("google", oldProfile.googlePlusUrl, updatedProfile.googlePlusUrl),
      composeNotification("linkedin", oldProfile.linkedInUrl, updatedProfile.linkedInUrl),
      composeNotification("blog", existing.blog, updated.blog))
    val nonEmptyNotifications = notifications.filterNot(_.isEmpty)
    nonEmptyNotifications.headOption map { first ⇒
      val prefix = "%s updated her/his social profile.".format(updated.fullName)
      val msg = nonEmptyNotifications.tail.foldLeft(first.get.capitalize)(_ + ", " + _.get)
      Some(prefix + " " + msg)
    } getOrElse None
  }

  /**
   * Retrieve facilitator statistics by brand, including years of experience,
   *  number of events and rating
   * @param id Facilitator id
   */
  protected def retrieveByBrandStatistics(id: Long) = {
    val licenses = licenseService.licenses(id).sortBy(_.brand.name)
    val events = eventService.
      findByFacilitator(id, brandId = None, future = Some(false)).
      filter(_.confirmed).groupBy(_.brandId).map(x => (x._1, x._2.length))
    val facilitation = facilitatorService.findByPerson(id)
    licenses.map { x ⇒
      (x.brand.id.get,
        x.brand.name,
        facilitation.find(_.brandId == x.license.brandId).get.rating,
        x.license.length.getStandardDays / 365,
        events.find(_._1 == x.brand.id.get).map(_._2).getOrElse(0))
    }
  }

  /**
   * Composes notification if the given value was updated
   *
   * @param msgType Notification type
   * @param old Old value
   * @param updated New value
   */
  protected def composeNotification(msgType: String,
    old: Option[String],
    updated: Option[String]): Option[String] = {
    if (updated.isDefined && old != updated)
      Some(notificatonMsg(msgType, updated.get))
    else
      None
  }

  protected def notificatonMsg(msgType: String, value: String): String =
    msgType match {
      case "twitter" ⇒ "follow her/him on <http://twitter.com/%s|Twitter>".format(value)
      case "facebook" ⇒ "become friends on <%s|Facebook>".format(value)
      case "google" ⇒ "add her/him to your circles on <%s|G+>".format(value)
      case "linkedin" ⇒ "connect on <%s|LinkedIn>".format(value)
      case "blog" ⇒ "read his/her blog <%s|here>".format(value)
      case _ ⇒ ""
    }
}

object People {

  /**
   * HTML form mapping for a person’s address.
   */
  val addressMapping = mapping(
    "id" -> ignored(Option.empty[Long]),
    "street1" -> optional(text),
    "street2" -> optional(text),
    "city" -> optional(text),
    "province" -> optional(text),
    "postCode" -> optional(text),
    "country" -> nonEmptyText)(Address.apply)(Address.unapply)

  /**
   * HTML form mapping for a person’s social profile.
   */
  val socialProfileMapping = mapping(
    "twitterHandle" -> optional(text.verifying(Constraints.pattern("""[A-Za-z0-9_]{1,16}""".r, error = "error.twitter"))),
    "facebookUrl" -> optional(facebookProfileUrl),
    "linkedInUrl" -> optional(linkedInProfileUrl),
    "googlePlusUrl" -> optional(googlePlusProfileUrl))({
      (twitterHandle, facebookUrl, linkedInUrl, googlePlusUrl) ⇒
        SocialProfile(0, ProfileType.Person, "", twitterHandle, facebookUrl,
          linkedInUrl, googlePlusUrl)
    })({
      (s: SocialProfile) ⇒
        Some(s.twitterHandle, s.facebookUrl,
          s.linkedInUrl, s.googlePlusUrl)
    })

  /**
   * HTML form mapping for creating and editing.
   */
  def personForm(editorName: String) = {
    Form(mapping(
      "id" -> ignored(Option.empty[Long]),
      "firstName" -> nonEmptyText,
      "lastName" -> nonEmptyText,
      "emailAddress" -> play.api.data.Forms.email,
      "birthday" -> optional(jodaLocalDate),
      "signature" -> boolean,
      "address" -> addressMapping,
      "bio" -> optional(text),
      "interests" -> optional(text),
      "profile" -> socialProfileMapping,
      "webSite" -> optional(webUrl),
      "blog" -> optional(webUrl),
      "active" -> ignored(true),
      "dateStamp" -> mapping(
        "created" -> ignored(DateTime.now()),
        "createdBy" -> ignored(editorName),
        "updated" -> ignored(DateTime.now()),
        "updatedBy" -> ignored(editorName))(DateStamp.apply)(DateStamp.unapply))(
        { (id, firstName, lastName, emailAddress, birthday, signature,
          address, bio, interests, profile, webSite, blog, active, dateStamp) ⇒
          {
            val person = Person(id, firstName, lastName, birthday, Photo.empty,
              signature, address.id.getOrElse(0), bio, interests,
              webSite, blog, customerId = None, virtual = false, active, dateStamp)
            person.socialProfile_=(profile.copy(email = emailAddress))
            person.address_=(address)
            person
          }
        })(
          { (p: Person) ⇒
            Some(
              (p.id, p.firstName, p.lastName, p.socialProfile.email, p.birthday,
                p.signature, p.address, p.bio, p.interests,
                p.socialProfile, p.webSite, p.blog, p.active, p.dateStamp))
          }))
  }
}
