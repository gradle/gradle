plugins {
    war
}

configurations {
    create("war") {
        isCanBeResolved = false
        outgoing {
            artifact(tasks["war"])
        }
    }
}


dependencies {
    // Shared libraries provided by parent EAR - use compileOnly to avoid packaging in WAR
    compileOnly("org.apache.commons:commons-lang3:3.14.0")
}
