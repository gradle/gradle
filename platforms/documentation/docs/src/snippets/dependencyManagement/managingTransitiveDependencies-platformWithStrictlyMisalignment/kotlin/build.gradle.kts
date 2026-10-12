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

    // The strictly constraint overrides the BOM for log4j-core
    implementation("org.apache.logging.log4j:log4j-core") {
        version {
            strictly("[2.17, 3[")
            prefer("2.17.0")
        }
    }
}
// end::platform-strictly-misalignment[]
