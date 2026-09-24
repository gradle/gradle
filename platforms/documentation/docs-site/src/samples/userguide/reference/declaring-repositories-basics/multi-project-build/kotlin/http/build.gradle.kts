// tag::root[]
plugins {
    java
    id("io.ratpack.ratpack-java")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":infra"))
    implementation(ratpack.dependency("dropwizard-metrics"))
}
// end::root[]

application {
    mainClass = "example.App"
}

ratpack.baseDir = file("src/ratpack/baseDir")
