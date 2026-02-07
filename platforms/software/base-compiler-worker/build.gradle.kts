plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Contains classes which all compiler workers leverage, regardless of ecosystem"

dependencies {
    api(projects.coreApi)
}
