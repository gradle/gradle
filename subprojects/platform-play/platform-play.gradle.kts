import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":messaging"))
    implementation(project(":processServices"))
    implementation(project(":logging"))
    implementation(project(":workerProcesses"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":files"))
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

    integTestImplementation(library("ant"))
    integTestRuntimeOnly(project(":compositeBuilds"))
    integTestRuntimeOnly(project(":idePlay"))

    testFixturesImplementation(project(":internalIntegTesting"))
    testFixturesImplementation(library("commons_io"))
    testFixturesImplementation(library("commons_httpclient"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core", "testFixtures")
    from(":platformNative", "testFixtures")
    from(":languageJvm", "testFixtures")
    from(":launcher", "testFixtures")
    from(":languageScala", "integTest")
    from(":languageJava", "integTest")
    from(":dependencyManagement")
    from(":diagnostics")
    from(":platformBase")
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
    }
}
