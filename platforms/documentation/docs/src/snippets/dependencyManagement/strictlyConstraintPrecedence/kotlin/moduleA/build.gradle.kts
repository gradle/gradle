plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::strictly-precedence-module[]
dependencies {
    constraints {
        api("org.apache.httpcomponents:httpclient") {
            version {
                strictly("4.5")  // This constraint is ignored
            }
        }
    }
}
// end::strictly-precedence-module[]
