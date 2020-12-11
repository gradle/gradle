import org.gradle.api.internal.runtimeshaded.PackageListGenerator

plugins {
    id("gradlebuild.distribution.implementation-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:core-api")
    implementation("org.gradle:core")
    implementation("org.gradle:file-temp"))
    implementation("org.gradle:wrapper")
    implementation("org.gradle:tooling-api")
    implementation(libs.commonsIo)
    api(libs.groovyTest)

    testImplementation(libs.guava)
    testImplementation(testFixtures("org.gradle:core"))

    integTestImplementation("org.gradle:native")
    integTestImplementation("org.gradle:logging")
    integTestImplementation("org.gradle:launcher")
    integTestImplementation("org.gradle:build-option")
    integTestImplementation("org.gradle:jvm-services")
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.jetbrainsAnnotations)

    testRuntimeOnly("org.gradle:distributions-core") {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}

val generateTestKitPackageList by tasks.registering(PackageListGenerator::class) {
    classpath = sourceSets.main.get().runtimeClasspath
    outputFile = file(layout.buildDirectory.file("runtime-api-info/test-kit-relocated.txt"))
}
tasks.jar {
    into("org/gradle/api/internal/runtimeshaded") {
        from(generateTestKitPackageList)
    }
}

classycle {
    excludePatterns.add("org/gradle/testkit/runner/internal/**")
}

tasks.integMultiVersionTest {
    systemProperty("org.gradle.integtest.testkit.compatibility", "all")
}
