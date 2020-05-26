plugins {
    gradlebuild.internal.java
    gradlebuild.distributions
}

dependencies {
    runtimeOnly(project(":distributionsMinimal"))

    runtimeOnly(project(":scala"))
    runtimeOnly(project(":ear"))
    runtimeOnly(project(":codeQuality"))
    runtimeOnly(project(":jacoco"))
    runtimeOnly(project(":testingJunitPlatform"))
    runtimeOnly(project(":ide"))
}
