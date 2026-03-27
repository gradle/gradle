plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::platform-strictly-misalignment[]
dependencies {
    // BOM recommends log4j-api:2.16.0 and log4j-core:2.16.0
    implementation(platform("org.apache.logging.log4j:log4j-bom:2.16.0"))

    // This overrides only log4j-core, leaving log4j-api at the BOM version
    implementation("org.apache.logging.log4j:log4j-core") {
        version {
            strictly("[2.17, 3[")
            prefer("2.17.0")
        }
    }
}
// Result: log4j-core resolves to 2.17.0, but log4j-api stays at 2.16.0
// end::platform-strictly-misalignment[]
