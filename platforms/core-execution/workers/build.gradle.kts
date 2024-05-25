plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Infrastructure for starting and managing worker processes"

dependencies {
    api(project(":base-services"))
    api(project(":build-operations"))
    api(project(":concurrent"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":hashing"))
    api(project(":java-language-extensions"))
    api(project(":logging"))
    api(project(":logging-api"))
    api(project(":messaging"))
    api(project(":model-core"))
    api(project(":process-services"))
    api(project(":serialization"))
    api(project(":service-provider"))
    api(project(":snapshots"))
    api(project(":worker-main"))
    api(project(":build-process-services"))

    api(libs.inject)
    api(libs.jsr305)

    implementation(project(":file-collections"))
    implementation(project(":time"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)

    testImplementation(project(":native"))
    testImplementation(project(":file-collections"))
    testImplementation(project(":resources"))
    testImplementation(project(":snapshots"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":logging")))

    integTestRuntimeOnly(project(":kotlin-dsl"))
    integTestRuntimeOnly(project(":kotlin-dsl-provider-plugins"))
    integTestRuntimeOnly(project(":api-metadata"))
    integTestRuntimeOnly(project(":test-kit"))

    integTestImplementation(project(":jvm-services"))
    integTestImplementation(project(":enterprise-operations"))

    testFixturesImplementation(libs.inject)
    testFixturesImplementation(libs.groovyJson)
    testFixturesImplementation(project(":base-services"))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm")) {
        because("Uses application plugin.")
    }
}
