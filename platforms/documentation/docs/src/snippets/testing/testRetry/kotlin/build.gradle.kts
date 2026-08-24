// tag::apply-plugin[]
plugins {
    java
    id("org.gradle.test-retry-bundled")
}
// end::apply-plugin[]

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// tag::configure-retry[]
tasks.test {
    // Pre-resolve the marker file outside the doFirst closure so the action doesn't
    // capture a reference to the enclosing script - which would break the config cache.
    val markerFile = layout.projectDirectory.file("marker.file").asFile
    doFirst {
        markerFile.delete()
    }

    // The build still succeeds even when tests fail after all retries so this sample can
    // demonstrate the actual retry behaviour in its output.
    ignoreFailures = true

    useJUnitPlatform()
    retry {
        maxRetries = 2
    }
}
// end::configure-retry[]
