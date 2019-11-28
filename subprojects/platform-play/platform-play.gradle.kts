import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
}

val integTestRuntimeResources by configurations.creating {
    isCanBeResolved = false
    isCanBeConsumed = false
}
val integTestRuntimeResourcesClasspath by configurations.creating {
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
    implementation(project(":baseServices"))
    implementation(project(":files"))
    implementation(project(":messaging"))
    implementation(project(":processServices"))
    implementation(project(":logging"))
    implementation(project(":workerProcesses"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":fileCollections"))
    implementation(project(":snapshots"))
    implementation(project(":dependencyManagement"))
    implementation(project(":workers"))
    implementation(project(":plugins"))
    implementation(project(":platformBase"))
    implementation(project(":platformJvm"))
    implementation(project(":languageJvm"))
    implementation(project(":languageJava"))
    implementation(project(":languageScala"))
    implementation(project(":testingBase"))
    implementation(project(":testingJvm"))
    implementation(project(":javascript"))
    implementation(project(":diagnostics"))
    implementation(project(":reporting"))

    implementation(library("groovy"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("inject"))

    testImplementation(project(":native"))
    testImplementation(project(":resources"))
    testImplementation(project(":baseServicesGroovy"))

    testRuntimeOnly(project(":runtimeApiInfo"))

    integTestImplementation(library("ant"))
    integTestRuntimeOnly(project(":compositeBuilds"))
    integTestRuntimeOnly(project(":idePlay"))
    integTestRuntimeOnly(project(":testingJunitPlatform"))

    testFixturesApi(project(":platformBase")) {
        because("Test fixtures export the Platform class")
    }
    testFixturesApi(testFixtures(project(":core")))
    testFixturesApi(testFixtures(project(":platformNative")))
    testFixturesApi(testFixtures(project(":languageJvm")))
    testFixturesApi(project(":internalIntegTesting"))
    testFixturesImplementation(project(":internalTesting"))
    testFixturesImplementation(project(":processServices"))
    testFixturesImplementation(library("commons_io"))
    testFixturesImplementation(library("commons_httpclient"))
    testFixturesImplementation(library("slf4j_api"))
    testFixturesApi(testFixtures(project(":languageScala")))
    testFixturesApi(testFixtures(project(":languageJava")))

    testImplementation(testFixtures(project(":dependencyManagement")))
    testImplementation(testFixtures(project(":diagnostics")))
    testImplementation(testFixtures(project(":platformBase")))

    integTestRuntimeResources(testFixtures(project(":platformPlay")))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

tasks.named<Test>("integTest") {
    exclude("org/gradle/play/prepare/**")
}

val integTestPrepare by tasks.registering(IntegrationTest::class) {
    systemProperties.put("org.gradle.integtest.executer", "embedded")
    if (BuildEnvironment.isCiServer) {
        systemProperties.put("org.gradle.integtest.multiversion", "all")
    }
    include("org/gradle/play/prepare/**")
    maxParallelForks = 1
}

tasks.withType<IntegrationTest>().configureEach {
    if (name != "integTestPrepare") {
        dependsOn(integTestPrepare)
        // this is a workaround for which we need a better fix:
        // it sets the platform play test fixtures resources directory in front
        // of the classpath, so that we can find them when executing tests in
        // an exploded format, rather than finding them in the test fixtures jar
        classpath = integTestRuntimeResourcesClasspath + classpath
    }
}
