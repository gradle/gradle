import gradlebuild.basics.PublicApi

plugins {
    id("gradlebuild.code-quality")
    id("gradlebuild.dependency-modules")
    id("gradlebuild.repositories")
    id("gradlebuild.strict-compile")
    id("gradlebuild.classycle")
    id("gradlebuild.unittest-and-compile")
    id("gradlebuild.binary-compatibility")
}

dependencies {
    currentClasspath(project(":distributions-full"))
    testImplementation(project(":base-services"))
    testImplementation(project(":model-core"))

    testImplementation(libs.archunitJunit4)
    testImplementation(libs.guava)

    testRuntimeOnly(project(":distributions-full"))
}

tasks.named("check").configure {
    dependsOn("codeQuality")
}

tasks.test {
    // Looks like loading all the classes requires more than the default 512M
    maxHeapSize = "700M"

    systemProperty("org.gradle.public.api.includes", PublicApi.includes.joinToString(":"))
    systemProperty("org.gradle.public.api.excludes", PublicApi.excludes.joinToString(":"))
}
