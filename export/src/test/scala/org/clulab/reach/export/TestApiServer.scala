package org.clulab.reach.export.server

import scala.concurrent.ExecutionContextExecutor

import com.typesafe.config.{ Config, ConfigFactory }

import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.{ Formats, DefaultFormats, jackson, native }

import akka.event.Logging
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest

import org.scalatest.{ Matchers, WordSpec }

import org.clulab.reach.export.server._
import org.clulab.reach.export.server.ApiServer._

/**
  * Unit tests of the API service class.
  *   Written by: Tom Hicks. 8/17/2017.
  *   Last Modified: Initial creation.
  */
class TestApiServer extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Json4sSupport
{

  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats

  implicit val executionContext = system.dispatcher
  val logger = Logging(system, getClass)

  val argMap = Map[String,String]()
  val appConfig = new AkkaServerConfig(argMap, Some("ApiServer"))
  val serverConfig = appConfig.config

  val apiService = new ApiService(appConfig)
  val route = apiService.makeRoute(serverConfig) // create the service route to test
  val version = serverConfig.getString("version")

  "The class under test" should {

    "return correct JSON version string" in {
      Get("/version") ~> route ~> check {
        status should equal(StatusCodes.OK)
        val resp = responseAs[Map[String,String]]
        (resp.get("version")) should equal (Some(version))
      }
    }

    // GETs

    "GET test" in {
      Get("/api/test?text=ZZZ4") ~> route ~> check {
        status should equal(StatusCodes.OK)
        val resp = responseAs[String]
        logger.info(s"resp=${resp}")        // DEBUGGING
        (resp) should not be (empty)
      }
    }

    "GET test2" in {
      Get("/api/test?text=ZZZ4") ~> route ~> check {
        status should equal(StatusCodes.OK)
        val resp = responseAs[String]
        logger.info(s"resp=${resp}")        // DEBUGGING
        (resp) should not be (empty)
      }
    }


    // POSTs

    "POST test" in {
      Post("/api/test",
        HttpEntity(ContentTypes.`application/json`, """{ "text": "ZZZ4" }""")) ~> route ~> check
        {
          status should equal(StatusCodes.OK)
          val resp = responseAs[String]
          logger.info(s"resp=${resp}")        // DEBUGGING
          (resp) should not be (empty)
        }
    }

    "POST test2" in {
      Post("/api/test2",
        HttpEntity(ContentTypes.`application/json`, """{ "text": "ZZZ4" }""")) ~> route ~> check
        {
          status should equal(StatusCodes.OK)
          val resp = responseAs[String]
          logger.info(s"resp=${resp}")        // DEBUGGING
          (resp) should not be (empty)
        }
    }

  }
}
