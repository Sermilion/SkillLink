plugins {
  id("skilllink.jvm-library")
  id("skilllink.quality")
  alias(libs.plugins.compose)
  alias(libs.plugins.compose.compiler)
}

dependencies {
  implementation(compose.desktop.currentOs)
  implementation(compose.material3)
  implementation(project(":application"))
  implementation(project(":domain"))
}
