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

package services

import models.releases.{WhatsRunningWhere, WhatsRunningWhereVersion}

object SampleReleasesData {
  val services: Seq[WhatsRunningWhere] = Seq(
    WhatsRunningWhere("example-payments-api", List(
      WhatsRunningWhereVersion("production", "1.8.0"),
      WhatsRunningWhereVersion("staging", "1.8.0"),
      WhatsRunningWhereVersion("qa", "1.7.0")
    )),
    WhatsRunningWhere("example-payments-frontend", List(WhatsRunningWhereVersion("production", "3.2.0"))),
    WhatsRunningWhere("example-audit-service", List(WhatsRunningWhereVersion("production", "2.4.0")))
  )
}
