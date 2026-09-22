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

import models.releases.WhatsRunningWhere
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

private[connectors] class WhatsRunningWhereCache(ttl: FiniteDuration, nanoTime: () => Long = () => System.nanoTime()) {
  private var cached: Option[(Long, Seq[WhatsRunningWhere])] = None
  private var inFlight: Option[Future[Seq[WhatsRunningWhere]]] = None

  def get(load: => Future[Seq[WhatsRunningWhere]])(implicit ec: ExecutionContext): Future[Seq[WhatsRunningWhere]] = synchronized {
    cached.filter { case (loadedAt, _) => nanoTime() - loadedAt < ttl.toNanos } match {
      case Some((_, values)) => Future.successful(values)
      case None => inFlight.getOrElse {
        val promise = Promise[Seq[WhatsRunningWhere]]()
        inFlight = Some(promise.future)
        Try(load).fold(Future.failed, identity).onComplete { result =>
          synchronized {
            cached = result.toOption.map(values => nanoTime() -> values)
            inFlight = None
            promise.complete(result)
          }
        }
        promise.future
      }
    }
  }
}
