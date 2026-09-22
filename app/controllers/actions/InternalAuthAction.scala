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

package controllers.actions

import javax.inject.Inject
import play.api.http.{HttpErrorHandler, Status}
import play.api.mvc.*
import uk.gov.hmrc.internalauth.client.{FrontendAuthComponents, IAAction, Predicate, Resource, ResourceLocation, ResourceType, Retrieval}
import scala.concurrent.{ExecutionContext, Future}

class InternalAuthRequest[A](request: Request[A], val canManageGuidance: Boolean, val username: String) extends WrappedRequest[A](request)

object GuidancePermission {
  val write: Predicate = Predicate.Permission(Resource.from("vulnerabilities", "guidance"), IAAction("WRITE"))
}

trait InternalAuthAction extends ActionBuilder[InternalAuthRequest, AnyContent] with ActionFunction[Request, InternalAuthRequest]

class DefaultInternalAuthAction @Inject() (
  auth: FrontendAuthComponents,
  errorHandler: HttpErrorHandler,
  val parser: BodyParsers.Default
)(implicit val executionContext: ExecutionContext) extends InternalAuthAction {
  override def invokeBlock[A](request: Request[A], block: InternalAuthRequest[A] => Future[Result]): Future[Result] =
    auth.authorizedAction(
      continueUrl = Call("GET", request.uri),
      predicate = Predicate.Permission(Resource(ResourceType("vulnerabilities-frontend"), ResourceLocation("*")), IAAction("READ")),
      retrieval = Retrieval.hasPredicate(GuidancePermission.write) ~ Retrieval.username,
      onForbiddenError = errorHandler.onClientError(request, Status.FORBIDDEN)
    ).invokeBlock[A](request, authenticated => block(new InternalAuthRequest(authenticated.request,
      authenticated.retrieval.a, authenticated.retrieval.b.value)))
}

class GuidanceWriteAction @Inject() (errorHandler: HttpErrorHandler)(implicit val executionContext: ExecutionContext)
  extends ActionFilter[InternalAuthRequest] {
  override protected def filter[A](request: InternalAuthRequest[A]): Future[Option[Result]] =
    if (request.canManageGuidance) {
      Future.successful(None)
    } else {
      errorHandler.onClientError(request, Status.FORBIDDEN).map(Some(_))
    }
}
