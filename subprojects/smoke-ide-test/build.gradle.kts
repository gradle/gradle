import gradlebuild.basics.gradleProperty
import gradlebuild.integrationtests.tasks.SmokeIdeTest
import gradlebuild.integrationtests.addDependenciesAndConfigurations
import gradlebuild.integrationtests.ide.AndroidStudioProvisioningExtension

plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.android-studio-provisioning")
}

description = "Tests are checking Gradle behavior during IDE synchronization process"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    maven {
        url = uri("https://www.jetbrains.com/intellij-repository/releases")
        content {
            includeGroup("com.jetbrains.intellij.tools")
        }
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

androidStudioProvisioning {
    androidStudioVersion = "2023.3.1.13"
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
    jvmArgumentProviders.add(
        SmokeIdeTestSystemProperties(
            gradleProperty("ideaHome"),
            gradleProperty("studioHome")
        )
    )

    val jvmArgumentProvider = project.extensions.getByType<AndroidStudioProvisioningExtension>()
        .androidStudioSystemProperties(project, emptyList())
    jvmArgumentProviders.add(jvmArgumentProvider)
}

tasks.withType<GroovyCompile>().configureEach {
    options.release = 17
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

class SmokeIdeTestSystemProperties(
    @get:Internal
    val ideaHome: Provider<String>,

    @get:Internal
    val studioHome: Provider<String>
) : CommandLineArgumentProvider {
    override fun asArguments(): MutableIterable<String> = buildList {
        add("-Dstudio.tests.headless=true")
        if (ideaHome.isPresent) {
            add("-DideaHome=${ideaHome.get()}")
        }
        if (studioHome.isPresent) {
            add("-DstudioHome=${studioHome.get()}")
        }
    }.toMutableList()
}

dependencies {
    smokeIdeTestImplementation(libs.gradleProfiler) {
        version {
            strictly("0.21.17-alpha-6")
            because("IDE provisioning requires special version of profiler compiled with Java 17")
        }

//      This dep is conflicting with the version from `:distributions-full` project.
        exclude("io.grpc")
    }
    smokeIdeTestDistributionRuntimeOnly(project(":distributions-full")) {
        because("Tests starts an IDE with using current Gradle distribution")
    }
}
