plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::three-levels-moduleA[]
dependencies {
    api(project(":moduleB"))
    constraints {
        api("org.apache.httpcomponents:httpclient") {
            version {
                // Level 2 (subproject): narrower strict range.
                strictly("[4.3, 4.5[")
            }
        }
    }
}
// end::three-levels-moduleA[]
