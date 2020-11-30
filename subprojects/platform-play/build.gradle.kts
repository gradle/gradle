import gradlebuild.basics.BuildEnvironment
import gradlebuild.integrationtests.tasks.IntegrationTest

plugins {
    id("gradlebuild.distribution.api-java")
}

val integTestRuntimeResources: Configuration by configurations.creating {
    isCanBeResolved = false
    isCanBeConsumed = false
}
val integTestRuntimeResourcesClasspath: Configuration by configurations.creating {
    extendsFrom(integTestRuntimeResources)
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        // play test apps MUST be found as exploded directory
        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements::class.java, LibraryElements.RESOURCES))
    }
    isTransitive = false
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":files"))
    implementation(project(":messaging"))
    implementation(project(":process-services"))
    implementation(project(":logging"))
    implementation(project(":worker-processes"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":file-collections"))
    implementation(project(":snapshots"))
    implementation(project(":dependency-management"))
    implementation(project(":workers"))
    implementation(project(":plugins"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":language-jvm"))
    implementation(project(":language-java"))
    implementation(project(":language-scala"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))
    implementation(project(":javascript"))
    implementation(project(":diagnostics"))
    implementation(project(":reporting"))

    implementation(libs.groovy)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)

    testImplementation(project(":native"))
    testImplementation(project(":resources"))
    testImplementation(project(":base-services-groovy"))

    integTestImplementation(libs.ant)
    integTestRuntimeOnly(project(":composite-builds"))
    integTestRuntimeOnly(project(":ide-play"))
    integTestRuntimeOnly(project(":testing-junit-platform"))

    testFixturesApi(project(":platform-base")) {
        because("Test fixtures export the Platform class")
    }
    testFixturesApi(testFixtures(project(":core")))
    testFixturesApi(testFixtures(project(":platform-native")))
    testFixturesApi(testFixtures(project(":language-jvm")))
    testFixturesApi(project(":internal-integ-testing"))
    testFixturesImplementation(project(":process-services"))
    testFixturesImplementation(libs.commonsIo)
    testFixturesImplementation(libs.commonsHttpclient)
    testFixturesImplementation(libs.slf4jApi)
    testFixturesApi(testFixtures(project(":language-scala")))
    testFixturesApi(testFixtures(project(":language-java")))

    testImplementation(testFixtures(project(":dependency-management")))
    testImplementation(testFixtures(project(":diagnostics")))
    testImplementation(testFixtures(project(":platform-base")))

    integTestDistributionRuntimeOnly(project(":distributions-full"))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestRuntimeResources(testFixtures(project(":platform-play")))
}

strictCompile {
    ignoreRawTypes() // deprecated raw types
    ignoreDeprecations() // uses deprecated software model types
}
val integTestPrepare by tasks.registering(IntegrationTest::class) {
    systemProperties["org.gradle.integtest.executer"] = "embedded"
    if (BuildEnvironment.isCiServer) {
        systemProperties["org.gradle.integtest.multiversion"] = "all"
    }
    include("org/gradle/play/prepare/**")
    maxParallelForks = 1
}

tasks.withType<IntegrationTest>().configureEach {
    if (name != "integTestPrepare") {
        dependsOn(integTestPrepare)
        exclude("org/gradle/play/prepare/**")
        // this is a workaround for which we need a better fix:
        // it sets the platform play test fixtures resources directory in front
        // of the classpath, so that we can find them when executing tests in
        // an exploded format, rather than finding them in the test fixtures jar
        classpath = integTestRuntimeResourcesClasspath + classpath
    }
}

integTest.usesSamples.set(true)
