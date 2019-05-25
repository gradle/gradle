import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty
import org.gradle.build.GradleStartScriptGenerator
import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))

    compileOnly(project(":launcherBootstrap"))
    compileOnly(project(":launcherStartup"))

    runtimeOnly(project(":baseServices"))
    runtimeOnly(project(":jvmServices"))
    runtimeOnly(project(":core"))
    runtimeOnly(project(":cli"))
    runtimeOnly(project(":buildOption"))
    runtimeOnly(project(":toolingApi"))
    runtimeOnly(project(":native"))
    runtimeOnly(project(":logging"))
    runtimeOnly(project(":docs"))

    runtimeOnly(library("asm"))
    runtimeOnly(library("commons_io"))
    runtimeOnly(library("commons_lang"))
    runtimeOnly(library("slf4j_api"))

    testImplementation(project(":native"))
    testImplementation(project(":cli"))
    testImplementation(project(":processServices"))
    testImplementation(project(":coreApi"))
    testImplementation(project(":modelCore"))
    testImplementation(project(":files"))
    testImplementation(project(":resources"))
    testImplementation(project(":persistentCache"))
    testImplementation(project(":baseServicesGroovy"))
    testImplementation(project(":buildOption"))
    testImplementation(project(":jvmServices"))
    testImplementation(library("slf4j_api"))
    testImplementation(library("guava"))
    testImplementation(library("ant"))

    integTestImplementation(project(":persistentCache"))
    integTestImplementation(project(":internalIntegTesting"))
    integTestImplementation(library("slf4j_api"))
    integTestImplementation(library("guava"))
    integTestImplementation(library("commons_lang"))
    integTestImplementation(library("commons_io"))
    integTestRuntimeOnly(project(":plugins"))
    integTestRuntimeOnly(project(":languageNative"))

    testFixturesImplementation(project(":internalTesting"))
    testFixturesImplementation(project(":internalIntegTesting"))
}

val availableJavaInstallations = rootProject.availableJavaInstallations

// Needed for testing debug command line option (JDWPUtil)
val javaInstallationForTest = availableJavaInstallations.javaInstallationForTest
if (!javaInstallationForTest.javaVersion.isJava9Compatible) {
    dependencies {
        integTestRuntime(files(javaInstallationForTest.toolsJar))
    }
}

// If running on Java 8 but compiling with Java 9, Groovy code would still be compiled by Java 8, so here we need the tools.jar
val currentJavaInstallation = availableJavaInstallations.currentJavaInstallation
if (currentJavaInstallation.javaVersion.isJava8) {
    dependencies {
        integTestCompileOnly(files(currentJavaInstallation.toolsJar))
    }
}

gradlebuildJava {
    moduleType = ModuleType.STARTUP
}

testFixtures {
    from(":core")
    from(":languageJava")
    from(":messaging")
    from(":logging")
    from(":toolingApi")
}

val integTestTasks: DomainObjectCollection<IntegrationTest> by extra
integTestTasks.configureEach {
    maxParallelForks = Math.min(3, project.maxParallelForks)
}

val configureJar by tasks.registering {
    doLast {
        val classpath = listOf(":baseServices", ":coreApi", ":core").joinToString(" ") {
            project(it).tasks.jar.get().archiveFile.get().asFile.name
        }
        tasks.jar {
            from(project(":launcherBootstrap").sourceSets["main"].output.files)
            from(project(":launcherStartup").sourceSets["main"].output.files)
            manifest.attributes("Class-Path" to classpath)
        }
    }
}

tasks.jar {
    dependsOn(configureJar)
    manifest.attributes("Main-Class" to "org.gradle.launcher.GradleMain")
}

val startScripts = tasks.register<GradleStartScriptGenerator>("startScripts") {
    startScriptsDir = file("$buildDir/startScripts")
    launcherBootstrapClasspathFiles.from(tasks.jar.get().outputs.files)
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
