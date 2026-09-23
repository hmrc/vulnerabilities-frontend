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

package controllers

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.jsoup.Jsoup
import play.api.http.Status.*
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.libs.ws.DefaultBodyReadables.*
import play.api.libs.ws.DefaultBodyWritables.*
import support.IntegrationSpec

class StubGuidanceISpec extends IntegrationSpec {
  override protected def useVulnerabilityStub: Boolean = true

  "Stub guidance journey" - {
    "add, edit and delete guidance across real requests without publishing the audit comment" in {
      authorise(canManageGuidance = true)
      stubFor(get(urlEqualTo(releasesPath)).willReturn(okJson("[]")))
      val detail = "/vulnerabilities-frontend/vulnerabilities/DEMO-UNAVAILABLE"
      val edit = s"$detail/guidance/edit"
      val save = s"$detail/guidance"
      val delete = s"$detail/guidance/delete"
      val comment = "Private history entry"
      def submit(form: WSResponse, path: String, fields: Map[String, String]): Unit = {
        form.status mustBe OK
        val csrf = Jsoup.parse(form.body[String]).select("input[name=csrfToken]").attr("value")
        csrf must not be empty
        val result = app.injector.instanceOf[WSClient].url(s"http://localhost:$port$path").withFollowRedirects(false)
          .withCookies(form.cookies.toList*)
          .post((fields + ("csrfToken" -> csrf)).view.mapValues(Seq(_)).toMap).futureValue
        result.status mustBe SEE_OTHER
        result.header("Location").value mustBe s"$detail#platops"
      }
      val initial = Jsoup.parse(request(detail).get().futureValue.body[String])
      initial.select(s"a[href='$edit']").text() mustBe "Add platform guidance"
      initial.select(s"a[href='$delete']").isEmpty mustBe true
      Seq("First guidance", "Replacement guidance").foreach { text =>
        submit(request(edit).get().futureValue, save, Map("guidance" -> text, "comment" -> comment))
        val page = Jsoup.parse(request(detail).get().futureValue.body[String])
        page.select("#platops .guidance-text").text() mustBe text
        page.text() must not include comment
        page.select(s"a[href='$edit']").text() mustBe "Edit guidance"
        page.select(s"a[href='$delete']").text() mustBe "Delete guidance"
      }
      submit(request(delete).get().futureValue, delete, Map("comment" -> comment))
      val removed = Jsoup.parse(request(detail).get().futureValue.body[String])
      removed.select("#platops").isEmpty mustBe true
      removed.select(s"a[href='$edit']").text() mustBe "Add platform guidance"
      removed.text() must not include comment
      verify(0, putRequestedFor(urlMatching("/vulnerabilities/api/.*")))
    }
  }
}
