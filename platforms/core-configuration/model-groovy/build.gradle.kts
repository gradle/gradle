plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Groovy specific adaptations to the model management."

dependencies {
    api(project(":base-services"))
    api(project(":model-core"))
    api(project(":base-services-groovy"))

    api(libs.jsr305)
    api(libs.groovy)

    implementation(projects.javaLanguageExtensions)
    implementation(project(":core-api"))

    implementation(libs.guava)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":model-core")))

    testRuntimeOnly(project(":distributions-core")) {
        because("NonTransformedModelDslBackingTest instantiates DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
