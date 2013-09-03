package models

import models.database.{ People, UserAccounts, LoginIdentities }
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick.DB
import play.api.libs.Crypto
import play.api.Play.current
import scala.slick.lifted.Query
import scala.util.Random
import securesocial.core._

/**
 * Contains profile and authentication info for a SecureSocial Identity.
 */
case class LoginIdentity(uid: Option[Long], identityId: IdentityId, firstName: String, lastName: String, fullName: String,
  email: Option[String], avatarUrl: Option[String], authMethod: AuthenticationMethod,
  oAuth1Info: Option[OAuth1Info], oAuth2Info: Option[OAuth2Info],
  passwordInfo: Option[PasswordInfo] = None, twitterHandle: String, apiToken: String) extends Identity {

  def person = DB.withSession { implicit session ⇒
    (for {
      account ← UserAccounts if account.twitterHandle === twitterHandle
      person ← People if person.id === account.personId

    } yield person).first
  }
}

object LoginIdentity {

  def apply(i: Identity): (String) ⇒ LoginIdentity = LoginIdentity(None, i.identityId, i.firstName, i.lastName, i.fullName, i.email,
    i.avatarUrl, i.authMethod, i.oAuth1Info, i.oAuth2Info, i.passwordInfo, _, generateApiToken(i))

  private def generateApiToken(i: Identity) = { Crypto.sign("%s-%s".format(i.identityId.userId, Random.nextInt())) }

  def findBytoken(token: String): Option[LoginIdentity] = DB.withSession {
    implicit session ⇒
      Query(LoginIdentities).filter(_.apiToken === token).list.headOption
  }

  def findByUid(uid: Long) = DB.withSession {
    implicit session ⇒
      val q = for {
        user ← LoginIdentities
        if user.uid is uid
      } yield user

      q.firstOption
  }

  def findByUserId(identityId: IdentityId): Option[LoginIdentity] = DB.withSession {
    implicit session ⇒
      val q = for {
        identity ← LoginIdentities
        if (identity.userId is identityId.userId) && (identity.providerId is identityId.providerId)
      } yield identity

      q.firstOption
  }

  def save(user: LoginIdentity) = DB.withSession {
    implicit session ⇒
      findByUserId(user.identityId) match {
        case None ⇒ {
          Activity.insert(user.fullName, Activity.Predicate.SignedUp)
          val uid = LoginIdentities.forInsert.insert(user)
          user.copy(uid = Some(uid))
        }
        case Some(existingUser) ⇒ {
          val userRow = for {
            u ← LoginIdentities
            if u.uid is existingUser.uid
          } yield u

          val updatedUser = user.copy(uid = existingUser.uid, apiToken = existingUser.apiToken)
          userRow.update(updatedUser)
          updatedUser
        }
      }
  }
}

