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

package models

import play.api.libs.json.{Json, Reads}

case class DirectoryTeam(name: String)
object DirectoryTeam { given Reads[DirectoryTeam] = Json.reads[DirectoryTeam] }

case class DirectoryService(name: String, digitalServiceName: Option[String])
object DirectoryService { given Reads[DirectoryService] = Json.reads[DirectoryService] }

case class OrganisationDirectory(teams: Seq[String], repositories: Seq[DirectoryService], digitalServices: Seq[String]) {
  val services: Seq[String] = repositories.map(_.name).distinct.sorted
  def includes(digitalService: String, service: String): Boolean =
    repositories.exists(repo => repo.name == service && repo.digitalServiceName.contains(digitalService))
}
object OrganisationDirectory {
  val empty: OrganisationDirectory = OrganisationDirectory(Seq.empty, Seq.empty, Seq.empty)
}
