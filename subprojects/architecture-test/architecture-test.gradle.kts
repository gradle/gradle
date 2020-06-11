import org.gradle.gradlebuild.PublicApi

plugins {
    gradlebuild.internal.java
    gradlebuild.`binary-compatibility`
}

dependencies {
    testImplementation(project(":baseServices"))
    testImplementation(project(":modelCore"))

    testImplementation(testLibrary("archunit_junit4"))
    testImplementation(library("guava"))

    testRuntimeOnly(project(":distributionsFull"))
}

tasks.withType<Test>().configureEach {
    // Looks like loading all the classes requires more than the default 512M
    maxHeapSize = "700M"

    systemProperty("org.gradle.public.api.includes", PublicApi.includes.joinToString(":"))
    systemProperty("org.gradle.public.api.excludes", PublicApi.excludes.joinToString(":"))
}
