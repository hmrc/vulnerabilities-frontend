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

package viewmodels

import java.net.URI
import scala.util.Try

object GuidanceText {
  case class Part(text: String, url: Option[String] = None)
  private val urlPattern = """https?://[^\s<>"']+""".r

  def parts(text: String): Seq[Part] = {
    val result = Vector.newBuilder[Part]
    var end = 0
    urlPattern.findAllMatchIn(text).foreach { matched =>
      result += Part(text.substring(end, matched.start))
      val candidate = matched.matched.reverse.dropWhile(".,;:!?)]}".contains(_)).reverse
      val url = Try(URI.create(candidate)).toOption.filter(_.getHost != null).map(_ => candidate)
      result += Part(candidate, url)
      result += Part(matched.matched.drop(candidate.length))
      end = matched.end
    }
    result += Part(text.substring(end))
    result.result()
  }
}
