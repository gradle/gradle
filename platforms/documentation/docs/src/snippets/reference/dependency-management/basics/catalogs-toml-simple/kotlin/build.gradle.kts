// tag::plugin[]
plugins {
    `java-library`
    alias(libs.plugins.versions)
}
// end::plugin[]

dependencies {
    api(libs.bundles.groovy)
}
