plugins {
    id("application")
}

repositories {
    mavenCentral()
}

// tag::dependency-full[]
dependencies {
    implementation("org.apache.httpcomponents:httpclient:4.5.4")
    implementation("commons-codec:commons-codec") {
        version {
            strictly("1.9")
        }
    }
}
// end::dependency-full[]

// tag::dependency-full-prefer[]
dependencies {
    implementation("commons-codec:commons-codec") {
        version {
            strictly("[1.9,2.0[")  // Allows versions >=1.9 and <2.0
            prefer("1.9")  // Prefers 1.9 but allows newer versions in range
        }
    }
}
// end::dependency-full-prefer[]

// tag::dependency-configs[]
configurations {
    compileClasspath {
        resolutionStrategy.force("commons-codec:commons-codec:1.9")
    }
}

dependencies {
    implementation("org.apache.httpcomponents:httpclient:4.5.4")
}
// end::dependency-configs[]
