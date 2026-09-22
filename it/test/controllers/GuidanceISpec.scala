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

class GuidanceISpec extends IntegrationSpec {
  private def editPath = s"$detailPath/guidance/edit"
  private def savePath = s"$detailPath/guidance"
  private def deletePath = s"$detailPath/guidance/delete"
  private def upstream = s"/vulnerabilities/api/v2/vulnerabilities/$vulnerabilityId/guidance"

  private def stubEditor(): Unit = {
    authorise(canManageGuidance = true)
    stubVulnerability()
    stubFor(get(urlEqualTo(advisoryPath.replace("=true", "=false"))).willReturn(okJson(fixture("vulnerability").toString)))
  }

  private def submit(form: WSResponse, path: String, fields: Map[String, String], csrf: Boolean = true): WSResponse = {
    val token = Jsoup.parse(form.body[String]).select("input[name=csrfToken]").attr("value")
    token must not be empty
    val data = if (csrf) fields + ("csrfToken" -> token) else fields
    app.injector.instanceOf[WSClient].url(s"http://localhost:$port$path").withFollowRedirects(false)
      .withCookies(form.cookies.toList*).post(data.view.mapValues(Seq(_)).toMap).futureValue
  }

  "Guidance editing over HTTP" - {
    "hide editing from readers and deny direct form access before calling the backend" in {
      authorise()
      stubVulnerability()
      val detail = request().get().futureValue
      detail.status mustBe OK
      Jsoup.parse(detail.body[String]).select(s"a[href='$editPath']").isEmpty mustBe true
      request(editPath).get().futureValue.status mustBe FORBIDDEN
      request(deletePath).get().futureValue.status mustBe FORBIDDEN
      verify(0, getRequestedFor(urlEqualTo(advisoryPath.replace("=true", "=false"))))
      verify(0, putRequestedFor(urlEqualTo(upstream)))
    }

    "publish guidance with CSRF protection and the IA author, ignoring a browser-supplied author" in {
      stubEditor()
      stubFor(put(urlEqualTo(upstream)).willReturn(noContent()))
      val detail = request().get().futureValue
      Jsoup.parse(detail.body[String]).select(s"a[href='$editPath']").text() mustBe "Edit guidance"
      val form = request(editPath).get().futureValue
      form.status mustBe OK
      Jsoup.parse(form.body[String]).select("textarea[name=guidance]").text() must include("Upgrade the dependency")
      val response = submit(form, savePath, Map("guidance" -> "Updated advice", "comment" -> "Private reason", "author" -> "spoofed-user"))
      response.status mustBe SEE_OTHER
      response.header("Location").value mustBe s"$detailPath#platops"
      verify(putRequestedFor(urlEqualTo(upstream)).withHeader("Authorization", equalTo(token))
        .withRequestBody(equalToJson("""{"guidance":"Updated advice","comment":"Private reason","author":"integration-user"}""")))
      verify(postRequestedFor(urlEqualTo(authPath)).withRequestBody(containing("WRITE")).withRequestBody(containing("username")))
    }

    "remove guidance through PUT with null and retain the private reason and IA author" in {
      stubEditor()
      stubFor(put(urlEqualTo(upstream)).willReturn(noContent()))
      val form = request(deletePath).get().futureValue
      form.status mustBe OK
      val response = submit(form, deletePath, Map("comment" -> "No longer applicable", "guidance" -> "ignored", "author" -> "spoofed"))
      response.status mustBe SEE_OTHER
      verify(putRequestedFor(urlEqualTo(upstream))
        .withRequestBody(equalToJson("""{"guidance":null,"comment":"No longer applicable","author":"integration-user"}""")))
    }

    "reject missing CSRF tokens and revoked write permission without mutating guidance" in {
      stubEditor()
      val form = request(editPath).get().futureValue
      submit(form, savePath, Map("guidance" -> "Advice", "comment" -> "Reason"), csrf = false).status mustBe FORBIDDEN
      authorise(canManageGuidance = false)
      submit(form, savePath, Map("guidance" -> "Advice", "comment" -> "Reason")).status mustBe FORBIDDEN
      verify(0, putRequestedFor(urlEqualTo(upstream)))
    }

    "retain valid input and show validation errors without a backend write" in {
      stubEditor()
      val form = request(editPath).get().futureValue
      val response = submit(form, savePath, Map("guidance" -> "Retain this advice", "comment" -> " "))
      response.status mustBe BAD_REQUEST
      val page = Jsoup.parse(response.body[String])
      page.select("textarea[name=guidance]").text() mustBe "Retain this advice"
      page.select("#comment-error").text() must include("Enter a reason")
      verify(0, putRequestedFor(urlEqualTo(upstream)))
    }

    "handle backend rejection and failure without a success redirect" in {
      stubEditor()
      val form = request(editPath).get().futureValue
      Seq(400 -> BAD_REQUEST, 403 -> FORBIDDEN, 404 -> NOT_FOUND, 500 -> BAD_GATEWAY).foreach { case (upstreamStatus, expected) =>
        stubFor(put(urlEqualTo(upstream)).willReturn(aResponse().withStatus(upstreamStatus)))
        val response = submit(form, savePath, Map("guidance" -> "Retain advice", "comment" -> "Reason"))
        response.status mustBe expected
        response.header("Location") mustBe None
        if (expected != NOT_FOUND) {
          Jsoup.parse(response.body[String]).select("textarea[name=guidance]").text() mustBe "Retain advice"
        }
      }
    }
  }
}
