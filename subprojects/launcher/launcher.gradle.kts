import org.gradle.build.GradleStartScriptGenerator
import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty

plugins {
    gradlebuild.distribution.`api-java`
}

val manifestClasspath by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = false

    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
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

    manifestClasspath(project(":bootstrap"))
    manifestClasspath(project(":baseServices"))
    manifestClasspath(project(":coreApi"))
    manifestClasspath(project(":core"))
    manifestClasspath(project(":persistentCache"))

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

    integTestImplementation(project(":persistentCache"))
    integTestImplementation(library("slf4j_api"))
    integTestImplementation(library("guava"))
    integTestImplementation(library("commons_lang"))
    integTestImplementation(library("commons_io"))

    testRuntimeOnly(project(":distributionsCore")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributionsNative")) {
        because("'native' distribution requried for 'ProcessCrashHandlingIntegrationTest.session id of daemon is different from daemon client'")
    }
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

// Needed for testing debug command line option (JDWPUtil) - 'CommandLineIntegrationSpec.can debug with org.gradle.debug=true'
val toolsJar = buildJvms.testJvm.map { jvm -> jvm.toolsClasspath }
dependencies {
    integTestRuntimeOnly(files(toolsJar))
}

tasks.jar.configure {
    val classpath = manifestClasspath.elements.map { classpathDependency ->
        classpathDependency.joinToString(" ") {
            it.asFile.name
        }
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
