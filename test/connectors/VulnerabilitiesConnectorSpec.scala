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
import models.api.{VulnerabilityDetails, VulnerabilityOccurrences, OccurrenceQuery, OccurrenceResolution, GuidanceUpdate}
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class VulnerabilitiesConnectorSpec extends SpecBase with HttpClientV2Support with WireMockSupport {
  private implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization("test-token")))
  private val response = {
    val stream = getClass.getResourceAsStream("/vulnerability-details.json")
    try Json.parse(stream).as[JsObject] finally stream.close()
  }
  private val occurrenceResponse = {
    val stream = getClass.getResourceAsStream("/vulnerability-occurrences.json")
    try Json.parse(stream).as[JsObject] finally stream.close()
  }
  private val occurrencesPath = "/vulnerabilities/api/v2/unique-vulnerabilities/CVE-2026-0001/occurrences"
  private val path = "/vulnerabilities/api/v2/vulnerabilities/CVE-2026-0001"

  private def connector(useStub: Boolean = false): VulnerabilitiesConnector = {
    val config = Configuration.from(Map(
      "microservice.services.vulnerabilities.protocol" -> "http",
      "microservice.services.vulnerabilities.host" -> wireMockHost,
      "microservice.services.vulnerabilities.port" -> wireMockPort,
      "vulnerabilities.use-stub" -> useStub
    ))
    new VulnerabilitiesConnector(httpClientV2, new ServicesConfig(config), config)
  }

  "Vulnerabilities connector" - {
    "send guidance, private comment and verified author, including explicit null for deletion" in {
      val target = "/vulnerabilities/api/v2/vulnerabilities/CVE-2026-0001/guidance"
      val client = connector()
      stubFor(put(urlEqualTo(target)).willReturn(noContent()))
      client.putGuidance("CVE-2026-0001", GuidanceUpdate(Some("Upgrade the dependency"), "Reviewed upstream advice", "platops-user")).futureValue
      verify(putRequestedFor(urlEqualTo(target)).withHeader("Authorization", equalTo("test-token"))
        .withRequestBody(equalToJson("""{"guidance":"Upgrade the dependency","comment":"Reviewed upstream advice","author":"platops-user"}""")))
      client.putGuidance("CVE-2026-0001", GuidanceUpdate(None, "Superseded", "platops-user")).futureValue
      verify(putRequestedFor(urlEqualTo(target))
        .withRequestBody(equalToJson("""{"guidance":null,"comment":"Superseded","author":"platops-user"}""")))
      Seq(400, 403, 404, 500).foreach { status =>
        stubFor(put(urlEqualTo(target)).willReturn(aResponse().withStatus(status)))
        client.putGuidance("CVE-2026-0001", GuidanceUpdate(None, "Remove guidance", "platops-user")).failed.futureValue mustBe a[UpstreamErrorResponse]
      }
    }

    "retain stub guidance changes without exposing comments as guidance" in {
      val client = connector(useStub = true)
      val id = "DEMO-UNAVAILABLE"
      client.getVulnerability(id).futureValue.value.guidance mustBe None
      client.putGuidance(id, GuidanceUpdate(Some("First guidance"), "Private reason", "alice")).futureValue
      client.getVulnerability(id).futureValue.value.guidance mustBe Some("First guidance")
      client.putGuidance(id.toLowerCase, GuidanceUpdate(Some("Replacement"), "Another private reason", "bob")).futureValue
      client.getVulnerability(id).futureValue.value.guidance mustBe Some("Replacement")
      client.putGuidance(id, GuidanceUpdate(None, "Outdated", "bob")).futureValue
      client.getVulnerability(id).futureValue.value.guidance mustBe None
      client.putGuidance("unknown", GuidanceUpdate(None, "Unknown", "alice")).failed.futureValue mustBe a[UpstreamErrorResponse]
      verify(0, putRequestedFor(urlMatching(".*")))
    }

    "return the existing sample without making HTTP requests in stub mode" in {
      val result = connector(useStub = true).getVulnerability("CVE-2021-44228").futureValue.value
      result.vulnerabilityId mustBe "CVE-2021-44228"
      result.summary mustBe "Remote code execution in Apache Log4j 2"
      result.cvss mustBe Some("10.0")
      result.occurrenceSummary mustBe None
      result.epss mustBe None
      verify(0, getRequestedFor(urlMatching(".*")))
    }

    "include the stub occurrence summary only when requested and return None for unknown IDs" in {
      val summary = connector(useStub = true).getVulnerability("CVE-2021-44228", true).futureValue.value.occurrenceSummary.value
      val occurrences = services.SampleVulnerabilityData.sample.occurrences
      summary.occurrences mustBe occurrences.size
      models.OccurrencePriority.values.foreach { priority =>
        (Json.toJson(summary.byPriority) \ priority.value).as[Int] mustBe occurrences.count(_.priority == priority)
      }
      models.OccurrenceState.values.foreach { state =>
        (Json.toJson(summary.byState) \ state.value).as[Int] mustBe occurrences.count(_.state == state)
      }
      connector(useStub = true).getVulnerability("unknown").futureValue mustBe None
      verify(0, getRequestedFor(urlMatching(".*")))
    }

    "default includeOccurrenceSummary to false and decode an omitted summary" in {
      stubFor(get(urlEqualTo(s"$path?includeOccurrenceSummary=false")).willReturn(okJson((response - "occurrenceSummary").toString)))
      val result = connector().getVulnerability("CVE-2026-0001").futureValue.value
      result.occurrenceSummary mustBe None
      result.publishedDate mustBe Instant.parse("2026-06-10T00:00:00Z")
      result.references must have size 2
      result.vulnerableComponents.head.componentVersion mustBe "2.4.0"
      result.vulnerableComponents.head.packageType mustBe "gav"
      result.fixedVersions mustBe Seq("2.4.1")
      result.cvss mustBe Some("9")
      result.epss mustBe Some(BigDecimal("0.14"))
      result.kev mustBe Some(true)
      result.guidance mustBe Some("Some guidance suggested...")
      verify(getRequestedFor(urlEqualTo(s"$path?includeOccurrenceSummary=false")).withHeader("Authorization", equalTo("test-token")))
    }

    "request occurrence aggregates explicitly and preserve the supplied counts" in {
      stubFor(get(urlEqualTo(s"$path?includeOccurrenceSummary=true")).willReturn(okJson(response.toString)))
      val result = connector().getVulnerability("CVE-2026-0001", true).futureValue.value
      val summary = result.occurrenceSummary.value
      summary.servicesAffected mustBe 6
      summary.teamsAffected mustBe 3
      summary.appVersionsAffected mustBe 8
      summary.occurrences mustBe 9
      summary.breachedOccurrences mustBe 2
      summary.nearBreachOccurrences mustBe 1
      summary.byPriority.expedite mustBe 3
      summary.byState.resolved mustBe 1
      Json.toJson(result).as[VulnerabilityDetails] mustBe result
    }

    "keep omitted or null enrichment unavailable and distinguish false and zero from missing data" in {
      val missing = response - "cvss" - "epss" - "kev" - "guidance" - "occurrenceSummary"
      val decoded = missing.as[VulnerabilityDetails]
      decoded.cvss mustBe None
      decoded.epss mustBe None
      decoded.kev mustBe None
      decoded.guidance mustBe None
      val nulls = (missing ++ Json.obj("cvss" -> play.api.libs.json.JsNull, "epss" -> play.api.libs.json.JsNull, "kev" -> play.api.libs.json.JsNull)).as[VulnerabilityDetails]
      nulls mustBe decoded
      val zero = (missing ++ Json.obj("cvss" -> "0", "epss" -> 0, "kev" -> false)).as[VulnerabilityDetails]
      zero.cvss mustBe Some("0")
      zero.epss mustBe Some(BigDecimal(0))
      zero.kev mustBe Some(false)
    }

    "return None for a 404 response" in {
      stubFor(get(urlEqualTo(s"$path?includeOccurrenceSummary=false")).willReturn(aResponse().withStatus(404)))
      connector().getVulnerability("CVE-2026-0001").futureValue mustBe None
    }

    "propagate server errors instead of silently replacing them with sample data" in {
      stubFor(get(urlEqualTo(s"$path?includeOccurrenceSummary=false")).willReturn(aResponse().withStatus(500)))
      connector().getVulnerability("CVE-2026-0001").failed.futureValue mustBe a[UpstreamErrorResponse]
    }

    "reject malformed responses instead of treating them as absent advisories" in {
      stubFor(get(urlEqualTo(s"$path?includeOccurrenceSummary=false")).willReturn(okJson("{}")))
      connector().getVulnerability("CVE-2026-0001").failed.futureValue mustBe a[Exception]
    }

    "decode occurrence evidence and SLA fields without adding optional query parameters" in {
      stubFor(get(urlEqualTo(occurrencesPath)).willReturn(okJson(occurrenceResponse.toString)))
      val result = connector().getOccurrences("CVE-2026-0001").futureValue
      result mustBe occurrenceResponse.as[VulnerabilityOccurrences]
      result.results.head.sla.daysRemaining mustBe -59
      result.results.head.sla.breached mustBe true
      result.results.head.importedBy.coordinate mustBe "uk.gov.hmrc:parent-lib:3.1.0"
      verify(getRequestedFor(urlEqualTo(occurrencesPath)).withHeader("Authorization", equalTo("test-token")))
    }

    "send all supported occurrence query parameters with safe encoding" in {
      stubFor(get(urlPathEqualTo(occurrencesPath)).willReturn(okJson(occurrenceResponse.toString)))
      val query = OccurrenceQuery(Some("A & B"), Some("Payments / tax"), Some(false), Some(true),
        Some("riskAccepted"), Some("expedite"), Some(10), Some(20), Some("firstDetected"), Some("desc"))
      connector().getOccurrences("CVE-2026-0001", query).futureValue
      val request = query.parameters.foldLeft(getRequestedFor(urlPathEqualTo(occurrencesPath))) {
        case (request, (key, value)) => request.withQueryParam(key, equalTo(value))
      }
      verify(request)
    }

    "preserve true, false and unavailable near-breach values from the occurrence response" in {
      val row = (occurrenceResponse \ "results").as[Seq[JsObject]].head
      val originalSla = (row \ "sla").as[JsObject]
      val cases = Seq(
        (originalSla ++ Json.obj("isNearBreach" -> true)) -> Some(true),
        (originalSla ++ Json.obj("isNearBreach" -> false)) -> Some(false),
        (originalSla ++ Json.obj("isNearBreach" -> play.api.libs.json.JsNull)) -> None,
        originalSla -> None
      )
      cases.foreach { case (sla, expected) =>
        val body = occurrenceResponse ++ Json.obj("results" -> Seq(row ++ Json.obj("sla" -> sla)))
        stubFor(get(urlEqualTo(occurrencesPath)).willReturn(okJson(body.toString)))
        connector().getOccurrences("CVE-2026-0001").futureValue.results.head.sla.isNearBreach mustBe expected
      }
    }

    "decode snake-case resolution evidence with an optional affected version" in {
      val raw = Json.obj("last_affected_app_version" -> "1.8.0", "fixed_in_app_version" -> "1.9.0",
        "resolved_at" -> "2026-09-22T09:30:00Z")
      Seq(raw -> Some("1.8.0"), (raw - "last_affected_app_version") -> None).foreach { case (resolution, previous) =>
        val row = (occurrenceResponse \ "results").as[Seq[JsObject]].head ++ Json.obj("resolution" -> resolution)
        val body = occurrenceResponse ++ Json.obj("results" -> Seq(row))
        stubFor(get(urlEqualTo(occurrencesPath)).willReturn(okJson(body.toString)))
        val result = connector().getOccurrences("CVE-2026-0001").futureValue.results.head.resolution.value
        result mustBe OccurrenceResolution(previous, "1.9.0", Instant.parse("2026-09-22T09:30:00Z"))
        Json.toJson(result) mustBe resolution
      }
    }

    "accept missing resolution but reject incomplete supplied resolution evidence" in {
      val row = (occurrenceResponse \ "results").as[Seq[JsObject]].head
      Seq(row, row ++ Json.obj("resolution" -> play.api.libs.json.JsNull)).foreach { value =>
        stubFor(get(urlEqualTo(occurrencesPath)).willReturn(okJson(
          (occurrenceResponse ++ Json.obj("results" -> Seq(value))).toString)))
        connector().getOccurrences("CVE-2026-0001").futureValue.results.head.resolution mustBe None
      }
      val incomplete = row ++ Json.obj("resolution" -> Json.obj("fixed_in_app_version" -> "1.9.0"))
      stubFor(get(urlEqualTo(occurrencesPath)).willReturn(okJson(
        (occurrenceResponse ++ Json.obj("results" -> Seq(incomplete))).toString)))
      connector().getOccurrences("CVE-2026-0001").failed.futureValue mustBe a[Exception]
    }

    "filter and page stub occurrences without HTTP and retain the unpaged result total" in {
      val query = OccurrenceQuery(teamName = Some("Example Payments"), digitalService = Some("Example payments"),
        priority = Some("immediate"), state = Some("actionable"), hasBreachedSla = Some(false), isNearBreach = Some(true),
        limit = Some(1), offset = Some(1), sortBy = Some("serviceName"), sortOrder = Some("desc"))
      val result = connector(useStub = true).getOccurrences("CVE-2021-44228", query).futureValue
      result.page.total mustBe 3
      result.results.map(_.serviceName) mustBe Seq("example-payments-api")
      connector(useStub = true).getOccurrences("unknown").futureValue.results mustBe empty
      verify(0, getRequestedFor(urlMatching(".*")))
    }

    "propagate occurrence endpoint failures and reject malformed bodies" in {
      Seq(404, 500).foreach { status =>
        stubFor(get(urlEqualTo(occurrencesPath)).willReturn(aResponse().withStatus(status)))
        connector().getOccurrences("CVE-2026-0001").failed.futureValue mustBe a[UpstreamErrorResponse]
      }
      stubFor(get(urlEqualTo(occurrencesPath)).willReturn(okJson("{}")))
      connector().getOccurrences("CVE-2026-0001").failed.futureValue mustBe a[Exception]
    }

  }
}
