/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package connectors

import base.SpecBase
import com.github.tomakehurst.wiremock.client.WireMock.*
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global

class TeamsAndRepositoriesConnectorSpec extends SpecBase with HttpClientV2Support with WireMockSupport {
  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val teams = "/api/v2/teams"
  private val services = "/api/v2/repositories?repoType=Service"
  private val digitalServices = "/api/v2/digital-services"

  private def connector(): TeamsAndRepositoriesConnector = {
    val config = Configuration.from(Map(
      "microservice.services.teams-and-repositories.protocol" -> "http",
      "microservice.services.teams-and-repositories.host" -> wireMockHost,
      "microservice.services.teams-and-repositories.port" -> wireMockPort,
      "teams-and-repositories.cache-ttl" -> "1 hour"
    ))
    new TeamsAndRepositoriesConnector(httpClientV2, new ServicesConfig(config), config)
  }

  private def stubDirectory(): Unit = {
    stubFor(get(urlEqualTo(teams)).willReturn(okJson("""[{"name":"Zulu"},{"name":"Alpha"},{"name":"Alpha"}]""")))
    stubFor(get(urlEqualTo(services)).willReturn(okJson("""[{"name":"b","digitalServiceName":"Payments"},{"name":"a"}]""")))
    stubFor(get(urlEqualTo(digitalServices)).willReturn(okJson("""["Payments","Accounts"]""")))
  }

  "Teams and repositories directory" - {
    "decode the API contracts, sort options and share one snapshot across concurrent requests" in {
      stubDirectory()
      val client = connector()
      val results = Future.sequence(Seq.fill(5)(client.getDirectory())).futureValue
      results.foreach { directory =>
        directory.teams mustBe Seq("Alpha", "Zulu")
        directory.services mustBe Seq("a", "b")
        directory.digitalServices mustBe Seq("Accounts", "Payments")
        directory.includes("Payments", "b") mustBe true
        directory.includes("Payments", "a") mustBe false
      }
      client.getDirectory().futureValue mustBe results.head
      Seq(teams, services, digitalServices).foreach(path => verify(1, getRequestedFor(urlEqualTo(path))))
    }

    "retry failed and malformed snapshots without retaining a partial directory" in {
      stubDirectory()
      val client = connector()
      stubFor(get(urlEqualTo(digitalServices)).willReturn(serverError()))
      client.getDirectory().failed.futureValue mustBe a[Exception]
      stubFor(get(urlEqualTo(digitalServices)).willReturn(okJson("[{}]")))
      client.getDirectory().failed.futureValue mustBe a[Exception]
      stubDirectory()
      client.getDirectory().futureValue.services mustBe Seq("a", "b")
      Seq(teams, services, digitalServices).foreach(path => verify(3, getRequestedFor(urlEqualTo(path))))
    }

    "expire the directory one hour after a successful fetch completes" in {
      var now = 0L
      val cache = new SnapshotCache[String](1.hour, () => now)
      val pending = Promise[String]()
      val first = cache.get(pending.future)
      now = 10.seconds.toNanos
      pending.success("original")
      first.futureValue mustBe "original"
      now += 1.hour.toNanos - 1
      cache.get(Future.successful("replacement")).futureValue mustBe "original"
      now += 1
      cache.get(Future.successful("replacement")).futureValue mustBe "replacement"
    }
  }
}
