plugins {
    id("skilllink.jvm-library")
    id("skilllink.quality")
}

dependencies {
    implementation(project(":desktop"))
    implementation(project(":infrastructure"))
    implementation(project(":application"))
}
