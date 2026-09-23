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
import models.releases.WhatsRunningWhere
import play.api.Configuration
import services.SampleReleasesData
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

@Singleton
class ReleasesConnector @Inject() (
  httpClient: HttpClientV2,
  servicesConfig: ServicesConfig,
  configuration: Configuration
)(implicit ec: ExecutionContext) {
  private val baseUrl = servicesConfig.baseUrl("releases-api")
  private val useStub = configuration.get[Boolean]("releases-api.use-stub")
  private val cache = new SnapshotCache[Seq[WhatsRunningWhere]](configuration.get[FiniteDuration]("releases-api.cache-ttl"))

  def getWhatsRunningWhere()(implicit hc: HeaderCarrier): Future[Seq[WhatsRunningWhere]] =
    if (useStub) {
      Future.successful(SampleReleasesData.services)
    } else {
      cache.get(httpClient.get(url"$baseUrl/releases-api/whats-running-where").execute[Seq[WhatsRunningWhere]])
    }

  def getWhatsRunningWhereForService(serviceName: String)(implicit hc: HeaderCarrier): Future[Option[WhatsRunningWhere]] =
    if (useStub) {
      Future.successful(SampleReleasesData.services.find(_.serviceName == serviceName))
    } else {
      httpClient.get(url"$baseUrl/releases-api/whats-running-where/$serviceName").execute[Option[WhatsRunningWhere]]
    }
}
