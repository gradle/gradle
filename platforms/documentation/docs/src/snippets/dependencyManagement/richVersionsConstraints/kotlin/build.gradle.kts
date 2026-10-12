plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::strictly-constraint-fix[]
dependencies {
    implementation("org.apache.httpcomponents:httpclient:4.5.4")

    constraints {
        implementation("commons-codec:commons-codec") {
            version {
                strictly("[1.0, 1.10[")
                prefer("1.9")
            }
            because("API we depend on was removed in 1.10")
        }
    }
}
// end::strictly-constraint-fix[]
