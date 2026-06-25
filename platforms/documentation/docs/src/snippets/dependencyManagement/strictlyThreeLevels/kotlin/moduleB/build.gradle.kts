plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::three-levels-moduleB[]
dependencies {
    constraints {
        api("org.apache.httpcomponents:httpclient") {
            version {
                // Level 3 (transitive subproject): even narrower, pinned to one minor.
                strictly("[4.3, 4.4[")
            }
        }
    }
}
// end::three-levels-moduleB[]
