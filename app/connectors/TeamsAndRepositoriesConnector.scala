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
import models.{DirectoryService, DirectoryTeam, OrganisationDirectory}
import play.api.Configuration
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

@Singleton
class TeamsAndRepositoriesConnector @Inject() (
  httpClient: HttpClientV2,
  servicesConfig: ServicesConfig,
  configuration: Configuration
)(implicit ec: ExecutionContext) {
  private val baseUrl = servicesConfig.baseUrl("teams-and-repositories")
  private val cache = new SnapshotCache[OrganisationDirectory](configuration.get[FiniteDuration]("teams-and-repositories.cache-ttl"))

  def getDirectory()(implicit hc: HeaderCarrier): Future[OrganisationDirectory] = cache.get {
    val teams = httpClient.get(url"$baseUrl/api/v2/teams").execute[Seq[DirectoryTeam]]
    val repositories = httpClient.get(url"$baseUrl/api/v2/repositories?repoType=Service").execute[Seq[DirectoryService]]
    val digitalServices = httpClient.get(url"$baseUrl/api/v2/digital-services").execute[Seq[String]]
    for {
      allTeams <- teams
      allRepositories <- repositories
      allDigitalServices <- digitalServices
    } yield OrganisationDirectory(allTeams.map(_.name).distinct.sorted, allRepositories, allDigitalServices.distinct.sorted)
  }
}
