import gradlebuild.integrationtests.addDependenciesAndConfigurations
import gradlebuild.integrationtests.tasks.SmokeIdeTest

plugins {
    id("gradlebuild.internal.kotlin")
}

description = "Tests are checking Gradle behavior during IDE synchronization process"

kotlin {
    jvmToolchain(17)
}

repositories {
    maven {
        url = uri("https://www.jetbrains.com/intellij-repository/releases")
    }

    maven {
        url = uri("https://cache-redirector.jetbrains.com/intellij-dependencies")
    }
}

val smokeIdeTestSourceSet = sourceSets.create("smokeIdeTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

addDependenciesAndConfigurations("smokeIde")
val smokeIdeTestImplementation: Configuration by configurations
val smokeIdeTestDistributionRuntimeOnly: Configuration by configurations

plugins.withType<IdeaPlugin> {
    with(model) {
        module {
            testSources.from(smokeIdeTestSourceSet.java.srcDirs, smokeIdeTestSourceSet.groovy.srcDirs)
            testResources.from(smokeIdeTestSourceSet.resources.srcDirs)
        }
    }
}

tasks.register<SmokeIdeTest>("smokeIdeTest") {
    group = "Verification"
    maxParallelForks = 1
    systemProperties["org.gradle.integtest.executer"] = "forking"
    testClassesDirs = smokeIdeTestSourceSet.output.classesDirs
    classpath = smokeIdeTestSourceSet.runtimeClasspath
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.withType<GroovyCompile>().configureEach {
    options.release = 17
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

tasks.named("archTest").configure {
    // ide-starter incompatible with arch tests
    enabled = false
}

dependencies {
    val ideStarterVersion = "233.14808.21"
    // These deps are `implementation` because of tests in Groovy
    // need some integration layer to use ide-starter, written in Kotlin
    implementation("com.jetbrains.intellij.tools:ide-starter-squashed:$ideStarterVersion")
    implementation("com.jetbrains.intellij.tools:ide-performance-testing-commands:$ideStarterVersion")
    implementation("org.kodein.di:kodein-di-jvm:7.21.1") {
        because("Ide-starter uses Kodein API to configure its behavior")
    }

    smokeIdeTestImplementation("com.jetbrains.intellij.tools:ide-starter-junit4:$ideStarterVersion")
    smokeIdeTestDistributionRuntimeOnly(project(":distributions-full")) {
        because("Tests starts an IDE with using current Gradle distribution")
    }
}
