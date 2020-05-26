plugins {
    gradlebuild.internal.java
    gradlebuild.distributions
}

dependencies {
    runtimeOnly(project(":distributionsJvm"))
    runtimeOnly(project(":distributionsNative"))
    runtimeOnly(project(":distributionsPublishing"))

    runtimeOnly(project(":buildInit"))
    runtimeOnly(project(":buildProfile"))
    runtimeOnly(project(":antlr"))

    // The following are scheduled to be removed from the distribution completely in Gradle 7.0
    runtimeOnly(project(":javascript"))
    runtimeOnly(project(":platformPlay"))
    runtimeOnly(project(":idePlay"))
}
