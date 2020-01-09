import org.gradle.build.GradleStartScriptGenerator
import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":cli"))
    implementation(project(":messaging"))
    implementation(project(":buildOption"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":files"))
    implementation(project(":fileCollections"))
    implementation(project(":snapshots"))
    implementation(project(":persistentCache"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":bootstrap"))
    implementation(project(":jvmServices"))
    implementation(project(":buildEvents"))
    implementation(project(":toolingApi"))

    implementation(library("groovy")) // for 'ReleaseInfo.getVersion()'
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("commons_io"))
    implementation(library("commons_lang"))
    implementation(library("asm"))
    implementation(library("ant"))

    runtimeOnly(library("asm"))
    runtimeOnly(library("commons_io"))
    runtimeOnly(library("commons_lang"))
    runtimeOnly(library("slf4j_api"))

    testImplementation(project(":internalIntegTesting"))
    testImplementation(project(":native"))
    testImplementation(project(":cli"))
    testImplementation(project(":processServices"))
    testImplementation(project(":coreApi"))
    testImplementation(project(":modelCore"))
    testImplementation(project(":resources"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":baseServicesGroovy")) // for 'Specs'

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":languageJava")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":logging")))
    testImplementation(testFixtures(project(":toolingApi")))

    testRuntimeOnly(project(":runtimeApiInfo"))
    testRuntimeOnly(project(":kotlinDsl"))

    integTestImplementation(project(":persistentCache"))
    integTestImplementation(project(":internalIntegTesting"))
    integTestImplementation(library("slf4j_api"))
    integTestImplementation(library("guava"))
    integTestImplementation(library("commons_lang"))
    integTestImplementation(library("commons_io"))
    integTestRuntimeOnly(project(":plugins"))
    integTestRuntimeOnly(project(":languageNative")) {
        because("for 'ProcessCrashHandlingIntegrationTest.session id of daemon is different from daemon client'")
    }
}

// Needed for testing debug command line option (JDWPUtil) - 'CommandLineIntegrationSpec.can debug with org.gradle.debug=true'
val toolsJar = buildJvms.testJvm.map { jvm -> jvm.toolsClasspath }
dependencies {
    integTestRuntimeOnly(files(toolsJar))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

tasks.jar {
    val classpath = listOf(":bootstrap", ":baseServices", ":coreApi", ":core").joinToString(" ") {
        project(it).tasks.jar.get().archiveFile.get().asFile.name
    }
    manifest.attributes("Class-Path" to classpath)
    manifest.attributes("Main-Class" to "org.gradle.launcher.GradleMain")
}

val startScripts = tasks.register<GradleStartScriptGenerator>("startScripts") {
    startScriptsDir = file("$buildDir/startScripts")
    launcherJar = tasks.jar.get().outputs.files
}

configurations {
    create("gradleScriptsElements") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, "start-scripts"))
        // TODO: Update GradleStartScriptGenerator to retain dependency information with Provider API
        outgoing.artifact(startScripts.map { it.startScriptsDir }) {
            builtBy(startScripts)
        }
    }
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
