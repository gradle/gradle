plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.binary-compatibility")
}

dependencies {
    currentClasspath("org.gradle:distributions-full")
    testImplementation("org.gradle:base-services")
    testImplementation("org.gradle:model-core")
    testImplementation("org.gradle:file-temp")

    testImplementation(libs.archunitJunit4)
    testImplementation(libs.guava)

    testRuntimeOnly("org.gradle:distributions-full")
}

tasks.test {
    // Looks like loading all the classes requires more than the default 512M
    maxHeapSize = "700M"

    systemProperty("org.gradle.public.api.includes", gradlebuild.basics.PublicApi.includes.joinToString(":"))
    systemProperty("org.gradle.public.api.excludes", gradlebuild.basics.PublicApi.excludes.joinToString(":"))
}
