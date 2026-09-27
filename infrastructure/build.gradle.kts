plugins {
    id("skilllink.jvm-library")
    id("skilllink.quality")
}

dependencies {
    implementation(project(":application"))
    implementation(project(":domain"))
    implementation(libs.sqlite.jdbc)
}
