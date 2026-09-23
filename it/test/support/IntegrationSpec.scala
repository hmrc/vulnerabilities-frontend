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

package support

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneServerPerTest
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{DefaultWSCookie, WSClient, WSRequest}
import play.api.mvc.{Session, SessionCookieBaker}
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.play.bootstrap.frontend.filters.crypto.SessionCookieCrypto

trait IntegrationSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures
  with IntegrationPatience with GuiceOneServerPerTest with WireMockSupport {

  protected val vulnerabilityId = "CVE-2099-1234"
  protected val detailPath = s"/vulnerabilities-frontend/vulnerabilities/$vulnerabilityId"
  protected val advisoryPath = s"/vulnerabilities/api/v2/vulnerabilities/$vulnerabilityId?includeOccurrenceSummary=true"
  protected val occurrencesPath = s"/vulnerabilities/api/v2/unique-vulnerabilities/$vulnerabilityId/occurrences"
  protected val releasesPath = "/releases-api/whats-running-where"
  protected val authPath = "/internal-auth/auth"
  protected val token = "Token integration-user"
  protected def useVulnerabilityStub: Boolean = false

  override def fakeApplication(): Application = {
    val upstreams = Seq("internal-auth", "catalogue-config", "vulnerabilities", "releases-api", "teams-and-repositories").flatMap { service =>
      Seq(
        s"microservice.services.$service.host" -> wireMockHost,
        s"microservice.services.$service.port" -> wireMockPort
      )
    }.toMap
    new GuiceApplicationBuilder().configure(upstreams ++ Map(
      "vulnerabilities.use-stub" -> useVulnerabilityStub,
      "releases-api.use-stub" -> false,
      "releases-api.cache-ttl" -> "60 seconds",
      "catalogue-frontend.base-url" -> "https://catalogue.example.test",
      "internal-auth.signInUrl" -> "/test-sign-in"
    )).build()
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    stubFor(get(urlEqualTo("/api/v2/teams")).willReturn(okJson(fixture("directory-teams").toString)))
    stubFor(get(urlEqualTo("/api/v2/repositories?repoType=Service")).willReturn(okJson(fixture("directory-services").toString)))
    stubFor(get(urlEqualTo("/api/v2/digital-services")).willReturn(okJson(fixture("directory-digital-services").toString)))
    stubFor(get(urlEqualTo("/catalogue-config/menu")).willReturn(okJson(fixture("catalogue-menu").toString)))
    stubFor(get(urlEqualTo("/catalogue-config/search-index")).willReturn(okJson("[]")))
  }

  protected def fixture(name: String): JsValue = {
    val stream = getClass.getResourceAsStream(s"/fixtures/$name.json")
    try Json.parse(stream) finally stream.close()
  }

  protected def authorise(canManageGuidance: Boolean = false): Unit = {
    stubFor(post(urlEqualTo(authPath)).willReturn(okJson(s"""{"retrievals": [$canManageGuidance, "integration-user"]}""")))
  }

  protected def stubVulnerability(): Unit = {
    stubFor(get(urlEqualTo(advisoryPath)).willReturn(okJson(fixture("vulnerability").toString)))
    stubFor(get(urlEqualTo(s"$occurrencesPath?limit=50&offset=0"))
      .willReturn(okJson(fixture("occurrences-first-page").toString)))
    stubFor(get(urlEqualTo(s"$occurrencesPath?limit=50&offset=2"))
      .willReturn(okJson(fixture("occurrences-second-page").toString)))
    stubFor(get(urlEqualTo(releasesPath)).willReturn(okJson(fixture("releases").toString)))
  }

  protected def request(path: String = detailPath, authToken: Option[String] = Some(token)): WSRequest = {
    val request = app.injector.instanceOf[WSClient].url(s"http://localhost:$port$path").withFollowRedirects(false)
    authToken.fold(request) { value =>

      val cookie = app.injector.instanceOf[SessionCookieBaker]
        .encodeAsCookie(Session(Map(SessionKeys.authToken -> value)))
      val encrypted = app.injector.instanceOf[SessionCookieCrypto].crypto.encrypt(PlainText(cookie.value)).value
      request.withCookies(DefaultWSCookie(cookie.name, encrypted))
    }
  }

  protected def verifyNoVulnerabilityCalls(): Unit = {
    verify(0, getRequestedFor(urlPathMatching("/vulnerabilities/api/.*")))
    verify(0, getRequestedFor(urlEqualTo(releasesPath)))
  }
}
