import gradlebuild.basics.PublicApi

plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.binary-compatibility")
}

dependencies {
    testImplementation(project(":base-services"))
    testImplementation(project(":modelCore"))

    testImplementation(libs.archunitJunit4)
    testImplementation(libs.guava)

    testRuntimeOnly(project(":distributions-full"))
}

tasks.withType<Test>().configureEach {
    // Looks like loading all the classes requires more than the default 512M
    maxHeapSize = "700M"

    systemProperty("org.gradle.public.api.includes", PublicApi.includes.joinToString(":"))
    systemProperty("org.gradle.public.api.excludes", PublicApi.excludes.joinToString(":"))
}
