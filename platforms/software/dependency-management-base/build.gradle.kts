plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Contains a subset of the dependency management API and implementation." +
    " Specifically, this project contains dependency management classes which do not depend on :core or :core-api"

dependencies {

    api(projects.baseServices)
    api(projects.snapshots)
    api(projects.stdlibJavaExtensions)

    api(libs.guava)
    api(libs.jsr305)

    implementation(projects.logging)

    implementation(libs.commonsLang3)

}

packageCycles {
    excludePatterns.add("org/gradle/api/artifacts/component")
}
