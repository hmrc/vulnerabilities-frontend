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

package forms

import base.SpecBase

class GuidanceFormsSpec extends SpecBase {
  "Guidance forms" - {
    "require meaningful guidance and an audit comment" in {
      GuidanceForms.save.bind(Map("guidance" -> "   ", "comment" -> "\n")).errors.map(_.key).toSet mustBe Set("guidance", "comment")
      GuidanceForms.save.bind(Map("guidance" -> "x" * 10001, "comment" -> "x" * 1001)).hasErrors mustBe true
      GuidanceForms.delete.bind(Map("comment" -> " ")).hasErrors mustBe true
      GuidanceForms.delete.bind(Map("comment" -> "x" * 1001)).hasErrors mustBe true
    }
    "trim content, accept the limits and bind no author from browser input" in {
      GuidanceForms.save.bind(Map("guidance" -> " guidance ", "comment" -> " reason ", "author" -> "someone-else")).value.value mustBe
        (("guidance", "reason"))
      GuidanceForms.save.bind(Map("guidance" -> "x" * 10000, "comment" -> "x" * 1000)).hasErrors mustBe false
      GuidanceForms.delete.bind(Map("comment" -> " deleted ", "guidance" -> "ignored")).value.value mustBe "deleted"
    }
  }
}
