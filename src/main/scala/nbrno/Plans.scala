package nbrno

import unfiltered.filter.Plan
import unfiltered.request._
import unfiltered.response._
import nbrno.domain.{Vote, User}
import org.json4s.DefaultFormats
import org.json4s.native.Serialization.{read, write}
import unfiltered.response.ResponseString
import org.slf4j.{LoggerFactory, Logger}
import unfiltered.Cookie

object RappersPlan extends Plan {
  implicit val formats = DefaultFormats
  val logger : Logger = LoggerFactory.getLogger("nbrno.RappersPlan")

  def intent = {
    case GET(_) & Path("/api/rappers")  => {
      ResponseString(write(DatabaseHandler.getRappersWithScore))
    }

    case req@Path(Seg("api" ::"rappers" :: rapperId :: "vote" :: Nil)) =>
      val body : String = Body.string(req)
      logger.info("RequestBody: " ++ body)
      req match {
      case POST(_) & Cookies(cookies) => req match {
        case RequestContentType("application/json;charset=UTF-8") => req match {
          case Accepts.Json(_) =>
            Ok ~> JsonContent ~> {
              val vote : Vote = read[Vote](body)
              val user : Option[User] = NbrnoServer.sessionStore.getUser(cookies("SESSION_ID").get.value)
              //TODO: Ensure rapperId is int
              if(user.isDefined){
                DatabaseHandler.vote(user.get.id.get, rapperId.toInt, vote.voteUp)
                ResponseString("Vote registered")
              }
              else Unauthorized
            }
          case _ => NotAcceptable
        }
        case _ => UnsupportedMediaType
      }
      case _ => MethodNotAllowed
    }
  }
}

object UserPlan extends Plan {
  implicit val formats = DefaultFormats
  val logger : Logger = LoggerFactory.getLogger("nbrno.UserPlan")

  def intent = {

    case req@Path("/api/user/signup") =>
      val body : String = Body.string(req)
      logger.info("RequestBody: " ++ body)
      req match {
      case POST(_) => req match {
        case RequestContentType("application/json;charset=UTF-8") => req match {
          case Accepts.Json(_) =>
            Ok ~> JsonContent ~> {
              val user: User = read[User](body)
              if (DatabaseHandler.availableUsername(user.username)) {
                val newUser : User = DatabaseHandler.createUser(user, req.remoteAddr)
                val sessionId : String = NbrnoServer.sessionStore.addUser(newUser)
                Ok ~> SetCookies(Cookie("SESSION_ID", sessionId, None, Some("/")))
              }
              else BadRequest ~> ResponseString("Username not available")
            }
          case _ => NotAcceptable
        }
        case _ => UnsupportedMediaType
      }
      case _ => MethodNotAllowed
    }

    case req@Path("/api/user/login") =>
      val body : String = Body.string(req)
      logger.info("RequestBody: " ++ body)
      req match {
      case POST(_) => req match {
        case RequestContentType("application/json;charset=UTF-8") => req match {
          case Accepts.Json(_) =>
            Ok ~> JsonContent ~> {
              val user: User = read[User](body)
              val validatedUser = DatabaseHandler.validateUser(user.username, user.password.get)
              if (validatedUser.isDefined) {
                val sessionId : String = NbrnoServer.sessionStore.addUser(validatedUser.get)
                Ok ~> SetCookies(Cookie("SESSION_ID", sessionId, None, Some("/")))
              }
              else BadRequest ~> ResponseString("Wrong username or password")
            }
          case _ => NotAcceptable
        }
        case _ => UnsupportedMediaType
      }
      case _ => MethodNotAllowed
    }
}


