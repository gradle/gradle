plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::same-level-ranges[]
dependencies {
    // Direct dependency with a wide strict range.
    api("org.apache.httpcomponents:httpclient") {
        version {
            strictly("[4.0, 5.0[")
        }
    }
    constraints {
        // Same module, narrower strict range that is fully inside the direct declaration's range.
        // If Gradle takes the intersection, the resolved version comes from [4.3, 4.5[ (some 4.4.x).
        // If the direct declaration overrides the constraint, the resolved version comes from [4.0, 5.0[ (4.5.14).
        // If same-level conflicts fail, the build fails.
        api("org.apache.httpcomponents:httpclient") {
            version {
                strictly("[4.3, 4.5[")
            }
        }
    }
}
// end::same-level-ranges[]
