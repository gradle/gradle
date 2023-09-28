plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Groovy specific adaptations to the model management."

dependencies {
    implementation(project(":base-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":base-services-groovy"))

    implementation(libs.groovy)
    implementation(libs.guava)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":model-core")))

    testRuntimeOnly(project(":distributions-core")) {
        because("NonTransformedModelDslBackingTest instantiates DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
