import gradlebuild.basics.BuildEnvironment
import gradlebuild.basics.FlakyTestStrategy
import gradlebuild.basics.buildCommitId
import gradlebuild.basics.flakyTestStrategy
import gradlebuild.integrationtests.addDependenciesAndConfigurations
import gradlebuild.integrationtests.tasks.SmokeTest
import gradlebuild.performance.generator.tasks.RemoteProject
import gradlebuild.integrationtests.androidhomewarmup.SdkVersion

plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.android-home-warmup")
}

val smokeTestSourceSet = sourceSets.create("smokeTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

jvmCompile {
    addCompilationFrom(smokeTestSourceSet)
}

dependencyAnalysis {
    issues {
        ignoreSourceSet(smokeTestSourceSet.name)
    }
}

addDependenciesAndConfigurations("smoke")

val smokeTestImplementation: Configuration by configurations
val smokeTestDistributionRuntimeOnly: Configuration by configurations

dependencies {
    testFixturesImplementation(projects.internalIntegTesting)

    smokeTestImplementation(projects.baseServices)
    smokeTestImplementation(projects.coreApi)
    smokeTestImplementation(projects.testKit)
    smokeTestImplementation(projects.launcher)
    smokeTestImplementation(projects.persistentCache)
    smokeTestImplementation(projects.internalTesting)
    smokeTestImplementation(projects.jvmServices)
    smokeTestImplementation(projects.buildOption)
    smokeTestImplementation(projects.processServices)
    smokeTestImplementation(libs.commonsIo)
    smokeTestImplementation(libs.groovyJson)
    smokeTestImplementation(libs.commonsHttpclient)
    smokeTestImplementation(libs.jgit)
    smokeTestImplementation(libs.spock)
    smokeTestImplementation(libs.junitPlatform)
    smokeTestImplementation(libs.jacksonDatabind)

    smokeTestImplementation(testFixtures(projects.buildProcessServices))
    smokeTestImplementation(testFixtures(projects.core))
    smokeTestImplementation(testFixtures(projects.modelReflect))
    smokeTestImplementation(testFixtures(projects.pluginDevelopment))
    smokeTestImplementation(testFixtures(projects.versionControl))

    smokeTestDistributionRuntimeOnly(projects.distributionsFull)
}

androidHomeWarmup {
    rootProjectDir = project.layout.projectDirectory.dir("../..")
    sdkVersions.set(
        listOf(
            // Build-tools 35.0.0 (used by AGP < 9.0)
            SdkVersion(compileSdk = 36, buildTools = "35.0.0", agpVersion = "8.13.1"),

            // Build-tools 36.0.0 (used by AGP >= 9.0)
            SdkVersion(compileSdk = 36, buildTools = "36.0.0", agpVersion = "9.0.0-beta02"),
        ),
    )
}

tasks {

    /**
     * Anroid project git URI.
     * Currently points to a clone of Now in Android.
     *
     * Note that you can change it to `file:///path/to/your/nowinandroid-clone/.git`
     * if you need to iterate quickly on changes to it.
     */
    val androidProjectGitUri = "https://github.com/gradle/nowinandroid.git"

    val androidProject by registering(RemoteProject::class) {
        remoteUri = androidProjectGitUri
        // latest https://github.com/gradle/nowinandroid/tree/smoke-tests-main as of 2025-11-05
        ref = "da342a6162b300e7d2cb15887d9cec3bb927c83e"
    }

    val gradleBuildCurrent by registering(RemoteProject::class) {
        remoteUri = rootDir.absolutePath
        ref = buildCommitId
    }

    val remoteProjects = arrayOf(androidProject, gradleBuildCurrent)

    if (BuildEnvironment.isCiServer) {
        remoteProjects.forEach { remoteProject ->
            remoteProject {
                doNotTrackState("Do a full checkout on CI")
            }
        }
    }

    register<Delete>("cleanRemoteProjects") {
        remoteProjects.forEach { remoteProject ->
            delete(remoteProject.map { it.outputDirectory })
        }
    }

    fun SmokeTest.configureForSmokeTest(remoteProjectOutputFiles: Any? = null, includes: List<String> = emptyList(), excludes: List<String> = emptyList()) {
        group = "Verification"
        testClassesDirs = smokeTestSourceSet.output.classesDirs
        classpath = smokeTestSourceSet.runtimeClasspath
        inputs.property("androidHomeIsSet", System.getenv("ANDROID_HOME") != null)
        inputs.property("androidSdkRootIsSet", System.getenv("ANDROID_SDK_ROOT") != null)

        if (remoteProjectOutputFiles != null) {
            inputs.files(remoteProjectOutputFiles)
                .withPropertyName("remoteProjectsSource")
                .ignoreEmptyDirectories()
                .withPathSensitivity(PathSensitivity.RELATIVE)
        }
        useJUnitPlatform {
            filter {
                isFailOnNoMatchingTests = (flakyTestStrategy != FlakyTestStrategy.ONLY)
                includes.forEach { includeTestsMatching(it) }
                excludes.forEach { excludeTestsMatching(it) }
            }
        }
    }

    fun SmokeTest.configureForSmokeTest(remoteProject: TaskProvider<RemoteProject>, includes: List<String> = emptyList(), excludes: List<String> = emptyList()) {
        configureForSmokeTest(remoteProject.map { it.outputDirectory }, includes, excludes)
    }

    val gradleBuildTestPattern = "org.gradle.smoketests.GradleBuild*SmokeTest"

    val androidProjectTestPattern = "org.gradle.smoketests.AndroidProject*SmokeTest"

    register<SmokeTest>("smokeTest") {
        description = "Runs Smoke tests"
        configureForSmokeTest(excludes = listOf(gradleBuildTestPattern, androidProjectTestPattern))
    }

    register<SmokeTest>("configCacheSmokeTest") {
        description = "Runs Smoke tests with the configuration cache"
        systemProperty("org.gradle.integtest.executer", "configCache")
        configureForSmokeTest(excludes = listOf(gradleBuildTestPattern, androidProjectTestPattern))
    }

    register<SmokeTest>("gradleBuildSmokeTest") {
        description = "Runs Smoke tests against the Gradle build"
        configureForSmokeTest(gradleBuildCurrent.map {
            project.fileTree(it.outputDirectory) {
                exclude("platforms/*/*/src/**")
                exclude("subprojects/*/src/**")
                exclude(".idea/**")
                exclude(".github/**")
                exclude(".teamcity/**")
            }
        }, includes = listOf(gradleBuildTestPattern))
    }

    register<SmokeTest>("androidProjectSmokeTest") {
        description = "Runs Android project Smoke tests"
        configureForSmokeTest(androidProject, includes = listOf(androidProjectTestPattern))
        maxParallelForks = 1 // those tests are pretty expensive, we shouldn't execute them concurrently

        dependsOn("androidHomeWarmup")
    }

    register<SmokeTest>("configCacheAndroidProjectSmokeTest") {
        description = "Runs Android project Smoke tests with the configuration cache"
        configureForSmokeTest(androidProject, includes = listOf(androidProjectTestPattern))
        maxParallelForks = 1 // those tests are pretty expensive, we shouldn't execute them concurrently
        jvmArgs("-Xmx700m")
        systemProperty("org.gradle.integtest.executer", "configCache")

        dependsOn("androidHomeWarmup")
    }
}

plugins.withType<IdeaPlugin>().configureEach {
    val smokeTestCompileClasspath: Configuration by configurations
    val smokeTestRuntimeClasspath: Configuration by configurations
    model.module {
        testSources.from(smokeTestSourceSet.java.srcDirs, smokeTestSourceSet.groovy.srcDirs)
        testResources.from(smokeTestSourceSet.resources.srcDirs)
        scopes["TEST"]!!["plus"]!!.add(smokeTestCompileClasspath)
        scopes["TEST"]!!["plus"]!!.add(smokeTestRuntimeClasspath)
    }
}

errorprone {
    nullawayEnabled = true
}
