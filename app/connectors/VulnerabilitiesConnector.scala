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

import javax.inject.{Inject, Singleton}
import models.api.{VulnerabilityDetails, VulnerabilityOccurrences, OccurrenceQuery, GuidanceUpdate}
import java.net.{URI, URLEncoder}
import java.nio.charset.StandardCharsets.UTF_8
import play.api.Configuration
import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue
import services.{SampleVulnerabilityData, StubGuidanceStore}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VulnerabilitiesConnector @Inject() (
  httpClient: HttpClientV2,
  servicesConfig: ServicesConfig,
  configuration: Configuration
)(implicit ec: ExecutionContext) {
  private val baseUrl = servicesConfig.baseUrl("vulnerabilities")
  private val useStub = configuration.get[Boolean]("vulnerabilities.use-stub")
  private val guidanceStore = new StubGuidanceStore

  def getVulnerability(
    vulnerabilityId: String,
    includeOccurrenceSummary: Boolean = false
  )(implicit hc: HeaderCarrier): Future[Option[VulnerabilityDetails]] =
    if (useStub) {
      Future.successful(SampleVulnerabilityData.details(vulnerabilityId, includeOccurrenceSummary)
        .map(details => details.copy(guidance = guidanceStore.current(details.vulnerabilityId, details.guidance))))
    } else {
      httpClient
        .get(url"$baseUrl/vulnerabilities/api/v2/vulnerabilities/$vulnerabilityId?includeOccurrenceSummary=${includeOccurrenceSummary.toString}")
        .execute[Option[VulnerabilityDetails]]
    }

  def putGuidance(vulnerabilityId: String, update: GuidanceUpdate)(implicit hc: HeaderCarrier): Future[Unit] =
    if (useStub) {
      SampleVulnerabilityData.details(vulnerabilityId, includeOccurrenceSummary = false) match {
        case None => Future.failed(UpstreamErrorResponse("Vulnerability not found", 404))
        case Some(details) =>
          guidanceStore.update(details.vulnerabilityId, update.guidance)
          Future.unit
      }
    } else {
      httpClient.put(url"$baseUrl/vulnerabilities/api/v2/vulnerabilities/$vulnerabilityId/guidance")
        .withBody(Json.toJson(update))
        .execute[Either[UpstreamErrorResponse, HttpResponse]].flatMap {
          case Right(response) if response.status == 204 => Future.unit
          case Right(response) => Future.failed(new IllegalStateException(s"Unexpected guidance response status: ${response.status}"))
          case Left(error) => Future.failed(error)
        }
    }

  def getOccurrences(
    vulnerabilityId: String,
    query: OccurrenceQuery = OccurrenceQuery()
  )(implicit hc: HeaderCarrier): Future[VulnerabilityOccurrences] =
    if (useStub) {
      Future.successful(SampleVulnerabilityData.occurrences(vulnerabilityId, query))
    } else {
      val endpoint = url"$baseUrl/vulnerabilities/api/v2/unique-vulnerabilities/$vulnerabilityId/occurrences"
      val queryString = query.parameters.map { case (key, value) =>
        s"$key=${URLEncoder.encode(value, UTF_8)}"
      }.mkString("&")
      val target = if (queryString.isEmpty) endpoint else URI.create(s"$endpoint?$queryString").toURL
      httpClient.get(target).execute[VulnerabilityOccurrences]
    }

}
