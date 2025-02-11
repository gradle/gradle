import gradlebuild.basics.BuildEnvironment
import gradlebuild.basics.buildCommitId
import gradlebuild.integrationtests.addDependenciesAndConfigurations
import gradlebuild.integrationtests.tasks.SmokeTest
import gradlebuild.performance.generator.tasks.RemoteProject

plugins {
    id("gradlebuild.internal.java")
}

val smokeTestSourceSet = sourceSets.create("smokeTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
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
    smokeTestImplementation(projects.jvmServices)
    smokeTestImplementation(projects.buildOption)
    smokeTestImplementation(projects.processServices)
    smokeTestImplementation(libs.commonsIo)
    smokeTestImplementation(libs.groovyAnt)
    smokeTestImplementation(libs.groovyJson)
    smokeTestImplementation(libs.commonsHttpclient)
    smokeTestImplementation(libs.jgit)
    smokeTestImplementation(libs.spock)
    smokeTestImplementation(libs.junitPlatform)
    smokeTestImplementation(libs.jacksonDatabind)

    smokeTestImplementation(testFixtures(projects.core))
    smokeTestImplementation(testFixtures(projects.pluginDevelopment))
    smokeTestImplementation(testFixtures(projects.versionControl))
    smokeTestImplementation(testFixtures(projects.modelReflect))

    smokeTestDistributionRuntimeOnly(projects.distributionsFull)
}

tasks {

    /**
     * Santa Tracker git URI.
     *
     * Note that you can change it to `file:///path/to/your/santa-tracker-clone/.git`
     * if you need to iterate quickly on changes to Santa Tracker.
     */
    val santaGitUri = "https://github.com/gradle/santa-tracker-android.git"

    val santaTracker by registering(RemoteProject::class) {
        remoteUri = santaGitUri
        // Pinned from branch main
        ref = "3f5f79b06da263670c77a734ec2db6220dcf311c"
    }

    val gradleBuildCurrent by registering(RemoteProject::class) {
        remoteUri = rootDir.absolutePath
        ref = buildCommitId
    }

    val remoteProjects = arrayOf(santaTracker, gradleBuildCurrent)

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

    fun SmokeTest.configureForSmokeTest(remoteProjectOutputFiles: Any? = null) {
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
    }

    fun SmokeTest.configureForSmokeTest(remoteProject: TaskProvider<RemoteProject>) {
        configureForSmokeTest(remoteProject.map { it.outputDirectory })
    }

    val gradleBuildTestPattern = "org.gradle.smoketests.GradleBuild*SmokeTest"

    val santaTrackerTestPattern = "org.gradle.smoketests.AndroidSantaTracker*SmokeTest"

    register<SmokeTest>("smokeTest") {
        description = "Runs Smoke tests"
        configureForSmokeTest()
        useJUnitPlatform {
            filter {
                excludeTestsMatching(gradleBuildTestPattern)
                excludeTestsMatching(santaTrackerTestPattern)
            }
        }
    }

    register<SmokeTest>("configCacheSmokeTest") {
        description = "Runs Smoke tests with the configuration cache"
        systemProperty("org.gradle.integtest.executer", "configCache")
        configureForSmokeTest()
        useJUnitPlatform {
            filter {
                excludeTestsMatching(gradleBuildTestPattern)
                excludeTestsMatching(santaTrackerTestPattern)
            }
        }
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
        })
        useJUnitPlatform {
            filter {
                includeTestsMatching(gradleBuildTestPattern)
            }
        }
    }

    register<SmokeTest>("santaTrackerSmokeTest") {
        description = "Runs Santa Tracker Smoke tests"
        configureForSmokeTest(santaTracker)
        maxParallelForks = 1 // those tests are pretty expensive, we shouldn't execute them concurrently
        useJUnitPlatform {
            filter {
                includeTestsMatching(santaTrackerTestPattern)
            }
        }
    }

    register<SmokeTest>("configCacheSantaTrackerSmokeTest") {
        description = "Runs Santa Tracker Smoke tests with the configuration cache"
        configureForSmokeTest(santaTracker)
        maxParallelForks = 1 // those tests are pretty expensive, we shouldn't execute them concurrently
        jvmArgs("-Xmx700m")
        systemProperty("org.gradle.integtest.executer", "configCache")
        useJUnitPlatform {
            filter {
                includeTestsMatching(santaTrackerTestPattern)
            }
        }
    }
}

plugins.withType<IdeaPlugin>().configureEach {
    val smokeTestCompileClasspath: Configuration by configurations
    val smokeTestRuntimeClasspath: Configuration by configurations
    model.module {
        testSources.from(smokeTestSourceSet.groovy.srcDirs)
        testResources.from(smokeTestSourceSet.resources.srcDirs)
        scopes["TEST"]!!["plus"]!!.add(smokeTestCompileClasspath)
        scopes["TEST"]!!["plus"]!!.add(smokeTestRuntimeClasspath)
    }
}
