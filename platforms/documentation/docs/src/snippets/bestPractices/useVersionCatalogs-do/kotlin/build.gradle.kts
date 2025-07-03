// tag::do-this[]
plugins {
    id("java-library")
    alias(libs.plugins.versions)
}
// end::do-this[]

repositories {
    mavenCentral()
}

// tag::do-this[]
dependencies {
    api(libs.bundles.groovy)
    testImplementation(libs.junit.jupiter)
    implementation(libs.commons.lang3)
}
// end::do-this[]
