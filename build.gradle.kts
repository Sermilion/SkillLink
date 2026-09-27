plugins {
  base
  id("skilllink.quality")
}

tasks.named("check") {
  dependsOn(subprojects.map { "${it.path}:check" })
  dependsOn(gradle.includedBuild("build-logic").task(":check"))
}

tasks.named("build") {
  dependsOn(tasks.named("check"))
  dependsOn(subprojects.map { "${it.path}:build" })
}
