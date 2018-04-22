import org.gradle.build.ClasspathManifest
import org.gradle.gradlebuild.testing.integrationtests.cleanup.EmptyDirectoryCheck
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
}

dependencies {
    api(project(":core"))
    api(project(":versionControl"))

    implementation(project(":resources"))
    implementation(project(":resourcesHttp"))

    implementation(library("asm"))
    implementation(library("asm_commons"))
    implementation(library("asm_util"))
    implementation(library("commons_lang"))
    implementation(library("commons_io"))
    implementation(library("ivy"))
    implementation(library("slf4j_api"))
    implementation(library("gson"))
    implementation(library("jcip"))
    implementation(library("maven3"))

    runtimeOnly(library("bouncycastle_provider"))
    runtimeOnly(project(":installationBeacon"))
    runtimeOnly(project(":compositeBuilds"))

    testImplementation(library("nekohtml"))

    integTestRuntimeOnly(project(":ivy"))
    integTestRuntimeOnly(project(":maven"))
    integTestRuntimeOnly(project(":resourcesS3"))
    integTestRuntimeOnly(project(":resourcesSftp"))
    integTestRuntimeOnly(project(":testKit"))

    testFixturesImplementation(project(":internalIntegTesting"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
    from(":messaging")
    from(":modelCore")
    from(":versionControl")
}

testFilesCleanup {
    isErrorWhenNotEmpty = false
}

val classpathManifest by tasks.getting(ClasspathManifest::class) {
    additionalProjects = listOf(project(":runtimeApiInfo"))
}
