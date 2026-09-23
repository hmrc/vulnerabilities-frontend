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
import models.releases.{WhatsRunningWhere, WhatsRunningWhereVersion}
import play.api.Configuration
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global

class ReleasesConnectorSpec extends SpecBase with HttpClientV2Support with WireMockSupport {
  private implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization("test-token")))
  private val path = "/releases-api/whats-running-where"
  private val body = """{"applicationName":"example-service","versions":[{"environment":"production","versionNumber":"1.2.3","lastDeployed":"2026-09-22T00:00:00Z"},{"environment":"staging","versionNumber":"1.2.4"}]}"""
  private val expected = WhatsRunningWhere("example-service", List(
    WhatsRunningWhereVersion("production", "1.2.3"), WhatsRunningWhereVersion("staging", "1.2.4")))

  private def connector(stub: Boolean = false): ReleasesConnector = {
    val config = Configuration.from(Map(
      "microservice.services.releases-api.protocol" -> "http",
      "microservice.services.releases-api.host" -> wireMockHost,
      "microservice.services.releases-api.port" -> wireMockPort,
      "releases-api.use-stub" -> stub,
      "releases-api.cache-ttl" -> "60 seconds"
    ))
    new ReleasesConnector(httpClientV2, new ServicesConfig(config), config)
  }

  "Releases connector" - {
    "decode the API field names and reuse a shared snapshot across callers" in {
      stubFor(get(urlEqualTo(path)).willReturn(okJson(s"[$body]").withFixedDelay(100)))
      val releases = connector()
      Future.sequence(Seq.fill(5)(releases.getWhatsRunningWhere())).futureValue.foreach(_ mustBe Seq(expected))
      releases.getWhatsRunningWhere().futureValue mustBe Seq(expected)
      verify(1, getRequestedFor(urlEqualTo(path)).withHeader("Authorization", equalTo("test-token")))
    }

    "read the service endpoint and return None for an unknown service" in {
      stubFor(get(urlEqualTo(s"$path/example-service")).willReturn(okJson(body)))
      connector().getWhatsRunningWhereForService("example-service").futureValue mustBe Some(expected)
      stubFor(get(urlEqualTo(s"$path/unknown")).willReturn(aResponse().withStatus(404)))
      connector().getWhatsRunningWhereForService("unknown").futureValue mustBe None
    }

    "serve fixtures without making HTTP calls" in {
      val releases = connector(stub = true)
      releases.getWhatsRunningWhere().futureValue.map(_.serviceName) must contain("example-payments-api")
      releases.getWhatsRunningWhereForService("example-payments-api").futureValue.value.versions.map(_.environment) must contain("production")
      releases.getWhatsRunningWhereForService("unknown").futureValue mustBe None
      verify(0, getRequestedFor(urlMatching(".*")))
    }

    "retry after failed or malformed responses instead of caching them" in {
      val releases = connector()
      stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(500)))
      releases.getWhatsRunningWhere().failed.futureValue mustBe a[UpstreamErrorResponse]
      stubFor(get(urlEqualTo(path)).willReturn(okJson("[{}]")))
      releases.getWhatsRunningWhere().failed.futureValue mustBe a[Exception]
      stubFor(get(urlEqualTo(path)).willReturn(okJson(s"[$body]")))
      releases.getWhatsRunningWhere().futureValue mustBe Seq(expected)
      verify(3, getRequestedFor(urlEqualTo(path)))
    }
  }

  "Deployment snapshot cache" - {
    "share an in-flight fetch and expire successful data after its TTL" in {
      var now = 0L
      val cache = new SnapshotCache[Seq[WhatsRunningWhere]](60.seconds, () => now)
      val pending = Promise[Seq[WhatsRunningWhere]]()
      var calls = 0
      def load() = { calls += 1; pending.future }
      val first = cache.get(load())
      val concurrent = cache.get(load())
      calls mustBe 1
      
      now = 30.seconds.toNanos
      pending.success(Seq(expected))
      first.futureValue mustBe Seq(expected)
      concurrent.futureValue mustBe Seq(expected)
      now = 89.seconds.toNanos
      cache.get(load()).futureValue mustBe Seq(expected)
      calls mustBe 1
      now = 90.seconds.toNanos
      cache.get { calls += 1; Future.successful(Seq.empty) }.futureValue mustBe empty
      calls mustBe 2
    }

    "allow zero TTL without retaining a completed snapshot" in {
      val cache = new SnapshotCache[Seq[WhatsRunningWhere]](Duration.Zero)
      cache.get(Future.successful(Seq(expected))).futureValue mustBe Seq(expected)
      cache.get(Future.successful(Seq.empty)).futureValue mustBe empty
    }
  }
}
