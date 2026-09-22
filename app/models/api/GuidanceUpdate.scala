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

package models.api

import play.api.libs.json.{JsNull, JsString, JsValue, Json, OWrites}

case class GuidanceUpdate(guidance: Option[String], comment: String, author: String)

object GuidanceUpdate {
  given OWrites[GuidanceUpdate] = OWrites { update =>
    Json.obj("guidance" -> update.guidance.fold[JsValue](JsNull)(JsString.apply), "comment" -> update.comment, "author" -> update.author)
  }
}
