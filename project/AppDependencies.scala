import sbt._

object AppDependencies {

  private val bootstrapVersion = "10.8.0"

  val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-frontend-play-30"    % bootstrapVersion,
    "uk.gov.hmrc"       %% "internal-auth-client-play-30"  % "4.4.0",
    "uk.gov.hmrc"       %% "catalogue-wrapper-play-30"     % "0.4.0"

  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"  % bootstrapVersion
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test
}
