// tag::do-this[]
plugins {
    id("java-library")
    alias(libs.plugins.versions)
}

repositories {
    mavenCentral()
}

dependencies {
    // SLF4J
    implementation(libs.slf4j.api)

    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformatCsv)

    // Groovy bundle
    api(libs.bundles.groovy)

    // Commons Lang
    implementation(libs.commons.lang3)
}
// end::do-this[]
