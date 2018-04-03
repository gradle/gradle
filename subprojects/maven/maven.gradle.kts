import org.gradle.gradlebuild.testing.integrationtests.cleanup.EmptyDirectoryCheck
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
}

dependencies {
    api(project(":core"))
    api(project(":dependencyManagement"))
    api(project(":plugins"))
    api(project(":pluginUse"))
    api(project(":publish"))

    implementation(library("slf4j_api"))
    implementation(library("groovy"))
    implementation(library("ivy"))
    implementation(library("maven3"))
    implementation(library("pmaven_common"))
    implementation(library("pmaven_groovy"))
    implementation(library("maven3_wagon_file"))
    implementation(library("maven3_wagon_http"))
    implementation(library("plexus_container"))
    implementation(library("aether_connector"))

    testImplementation(testLibrary("xmlunit"))

    integTestImplementation(project(":ear"))
    integTestRuntimeOnly(project(":resourcesS3"))
    integTestRuntimeOnly(project(":resourcesSftp"))

    testFixturesImplementation(project(":internalIntegTesting"))
}

gradlebuildJava {
    moduleType = ModuleType.PLUGIN
}

testFixtures {
    from(":core")
    from(":modelCore")
}

testFilesCleanup {
    isErrorWhenNotEmpty = false
}

