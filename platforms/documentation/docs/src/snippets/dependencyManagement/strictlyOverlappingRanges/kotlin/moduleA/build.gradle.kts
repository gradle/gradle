plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::overlapping-module[]
dependencies {
    constraints {
        api("org.apache.httpcomponents:httpclient") {
            version {
                // Narrower strict range, fully inside the root's range.
                // Intersection rule predicts the resolved version comes from this range (4.4.x).
                // Root-wins-unconditionally predicts the resolved version comes from root's range (4.5.x).
                strictly("[4.3, 4.5[")
            }
        }
    }
}
// end::overlapping-module[]
