// tag::do-this[]
// This is the build.gradle file for the util-guava module

plugins {
    `java-library`
}

dependencies {
    api(project(":util"))
    implementation("com.google.guava:guava:31.1-jre")
}
// end::do-this[]
