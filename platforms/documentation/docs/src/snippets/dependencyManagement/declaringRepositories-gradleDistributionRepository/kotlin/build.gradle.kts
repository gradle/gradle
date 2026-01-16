plugins {
    id("java-library")
}

// tag::gradle-distribution-repository[]
repositories {
    gradleDistribution()
}
// end::gradle-distribution-repository[]

// tag::declare-distribution-dependency[]
dependencies {
    implementation("org.apache.groovy:groovy:4+")
}
// end::declare-distribution-dependency[]
