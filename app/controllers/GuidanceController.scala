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

import javax.inject.Inject
import connectors.VulnerabilitiesConnector
import controllers.actions.{GuidanceWriteAction, InternalAuthAction, InternalAuthRequest}
import forms.GuidanceForms
import models.api.GuidanceUpdate
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc.*
import play.twirl.api.Html
import uk.gov.hmrc.cataloguewrapper.services.CatalogueWrapperService
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import views.html.{GuidanceFormView, VulnerabilityNotFoundView, VulnerabilityStyles}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class GuidanceController @Inject() (
  val controllerComponents: MessagesControllerComponents,
  internalAuth: InternalAuthAction,
  guidanceWrite: GuidanceWriteAction,
  connector: VulnerabilitiesConnector,
  wrapper: CatalogueWrapperService,
  view: GuidanceFormView,
  notFound: VulnerabilityNotFoundView,
  styles: VulnerabilityStyles
)(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport {
  private val editor = internalAuth.andThen(guidanceWrite)

  private def render(content: Html, title: String, status: Status)(implicit request: RequestHeader): Future[Result] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    wrapper.standardCatalogueLayout(content = content, pageTitle = Some(title), activeItemId = Some("vulnerabilities"),
      signOutUrl = Some(routes.VulnerabilityController.signOut().url), stylesheets = Seq(styles()), fullWidth = false)
      .map(status(_))
  }

  private def missing(implicit request: RequestHeader): Future[Result] =
    render(notFound("Vulnerability not found", "There is no advisory with this identifier."), "Vulnerability not found", NotFound)

  private def formPage(id: String, form: Form[?], deleting: Boolean, status: Status)(implicit request: RequestHeader): Future[Result] =
    render(view(id, form, deleting), if (deleting) "Delete platform guidance" else "Platform guidance", status)

  def edit(id: String): Action[AnyContent] = editor.async { implicit request =>
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    connector.getVulnerability(id).flatMap {
      case None => missing
      case Some(details) => formPage(details.vulnerabilityId, GuidanceForms.save.fill((details.guidance.getOrElse(""), "")), false, Ok)
    }
  }

  def confirmDelete(id: String): Action[AnyContent] = editor.async { implicit request =>
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    connector.getVulnerability(id).flatMap {
      case None => missing
      case Some(details) if details.guidance.isEmpty => Future.successful(Redirect(routes.VulnerabilityController.show(details.vulnerabilityId).url + "#platops"))
      case Some(details) => formPage(details.vulnerabilityId, GuidanceForms.delete, true, Ok)
    }
  }

  def save(id: String): Action[AnyContent] = editor.async { implicit request =>
    val form = GuidanceForms.save.bindFromRequest()
    form.fold(
      errors => formPage(id, errors, false, BadRequest),
      { case (guidance, comment) => update(id, GuidanceUpdate(Some(guidance), comment, request.username), form, false) }
    )
  }

  def delete(id: String): Action[AnyContent] = editor.async { implicit request =>
    val form = GuidanceForms.delete.bindFromRequest()
    form.fold(
      errors => formPage(id, errors, true, BadRequest),
      comment => update(id, GuidanceUpdate(None, comment, request.username), form, true)
    )
  }

  private def update(id: String, change: GuidanceUpdate, form: Form[?], deleting: Boolean)
    (implicit request: InternalAuthRequest[AnyContent]): Future[Result] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    connector.putGuidance(id, change).map { _ =>
      Redirect(routes.VulnerabilityController.show(id).url + "#platops")
        .flashing("guidanceSuccess" -> (if (deleting) "Platform guidance deleted." else "Platform guidance saved."))
    }.recoverWith {
      case error: UpstreamErrorResponse if error.statusCode == 403 =>
        formPage(id, form.withGlobalError("You no longer have permission to change platform guidance."), deleting, Forbidden)
      case error: UpstreamErrorResponse if error.statusCode == 404 => missing
      case error: UpstreamErrorResponse if error.statusCode == 400 =>
        formPage(id, form.withGlobalError("The guidance change was rejected. Check the text and reason for change."), deleting, BadRequest)
      case NonFatal(_) =>
        formPage(id, form.withGlobalError("Unable to confirm whether the change was saved. Check the vulnerability page before trying again."), deleting, BadGateway)
    }
  }
}
