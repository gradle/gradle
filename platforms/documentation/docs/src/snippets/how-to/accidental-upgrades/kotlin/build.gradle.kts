plugins {
    id("application")
}

repositories {
    mavenCentral()
}

// tag::dependency-full[]
dependencies {
    implementation("org.apache.commons:commons-lang3:3.2")

    constraints {
        implementation("org.apache.commons:commons-lang3:3.1") {
            because("Version 1.3 introduces breaking changes not yet handled")
        }
    }
}
// end::dependency-full[]

// tag::dependency-full-fail[]
configurations.all {
    resolutionStrategy.failOnVersionConflict()
}
// end::dependency-full-fail[]

// tag::dependency[]
dependencies {
    implementation("org.apache.commons:commons-lang3:3.2")
}
// end::dependency[]

// tag::dependency-const[]
dependencies {
    implementation("org.apache.commons:commons-lang3")
    constraints {
        implementation("org.apache.commons:commons-lang3") {
            version {
                strictly("3.1")
            }
        }
    }
}
// end::dependency-const[]

// tag::dependency-lock[]
configurations.all {
    resolutionStrategy.activateDependencyLocking()
}
// end::dependency-lock[]
