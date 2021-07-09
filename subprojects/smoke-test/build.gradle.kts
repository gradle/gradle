import gradlebuild.basics.BuildEnvironment
import gradlebuild.basics.accessors.groovy
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

val smokeTestImplementation: Configuration by configurations.getting
val smokeTestDistributionRuntimeOnly: Configuration by configurations.getting

dependencies {
    smokeTestImplementation(project(":base-services"))
    smokeTestImplementation(project(":core-api"))
    smokeTestImplementation(project(":test-kit"))
    smokeTestImplementation(project(":launcher"))
    smokeTestImplementation(project(":persistent-cache"))
    smokeTestImplementation(project(":jvm-services"))
    smokeTestImplementation(project(":build-option"))
    smokeTestImplementation(project(":process-services"))
    smokeTestImplementation(libs.commonsIo)
    smokeTestImplementation(libs.groovyAnt)
    smokeTestImplementation(libs.groovyJson)
    smokeTestImplementation(libs.commonsHttpclient)
    smokeTestImplementation(libs.jgit)
    smokeTestImplementation(libs.spock)
    smokeTestImplementation(libs.junitPlatform)

    smokeTestImplementation(testFixtures(project(":core")))
    smokeTestImplementation(testFixtures(project(":plugin-development")))
    smokeTestImplementation(testFixtures(project(":version-control")))
    smokeTestImplementation(testFixtures(project(":model-core")))

    smokeTestDistributionRuntimeOnly(project(":distributions-full"))
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
        remoteUri.set(santaGitUri)
        // Pinned from branch main
        ref.set("c26e2b8aa5c34758934009f1d5b0334f7fc2db5a")
    }

    val gradleBuildCurrent by registering(RemoteProject::class) {
        remoteUri.set(rootDir.absolutePath)
        ref.set(moduleIdentity.gradleBuildCommitId)
    }

    val remoteProjects = arrayOf(santaTracker, gradleBuildCurrent)

    if (BuildEnvironment.isCiServer) {
        remoteProjects.forEach { remoteProject ->
            remoteProject {
                outputs.upToDateWhen { false }
            }
        }
    }

    register<Delete>("cleanRemoteProjects") {
        remoteProjects.forEach { remoteProject ->
            delete(remoteProject.map { it.outputDirectory })
        }
    }

    fun SmokeTest.configureForSmokeTest(vararg remoteProjects: TaskProvider<RemoteProject>) {
        group = "Verification"
        testClassesDirs = smokeTestSourceSet.output.classesDirs
        classpath = smokeTestSourceSet.runtimeClasspath
        maxParallelForks = 1 // those tests are pretty expensive, we shouldn"t execute them concurrently
        inputs.property("androidHomeIsSet", System.getenv("ANDROID_HOME") != null)
        inputs.property("androidSdkRootIsSet", System.getenv("ANDROID_SDK_ROOT") != null)
        inputs.files(remoteProjects.map { it.map { it.outputDirectory } })
            .withPropertyName("remoteProjectsSource")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }

    val gradleBuildTestPattern = "org.gradle.smoketests.GradleBuild*SmokeTest"

    register<SmokeTest>("smokeTest") {
        description = "Runs Smoke tests"
        configureForSmokeTest(santaTracker)
        useJUnitPlatform {
            filter {
                excludeTestsMatching(gradleBuildTestPattern)
            }
        }
    }

    register<SmokeTest>("configCacheSmokeTest") {
        description = "Runs Smoke tests with the configuration cache"
        configureForSmokeTest(santaTracker)
        systemProperty("org.gradle.integtest.executer", "configCache")
        useJUnitPlatform {
            filter {
                excludeTestsMatching(gradleBuildTestPattern)
            }
        }
    }

    register<SmokeTest>("gradleBuildSmokeTest") {
        description = "Runs Smoke tests against the Gradle build"
        configureForSmokeTest(gradleBuildCurrent)
        useJUnitPlatform {
            filter {
                includeTestsMatching(gradleBuildTestPattern)
            }
        }
    }
}

plugins.withType<IdeaPlugin>().configureEach {
    val smokeTestCompileClasspath: Configuration by configurations.getting
    val smokeTestRuntimeClasspath: Configuration by configurations.getting
    model.module {
        testSourceDirs = testSourceDirs + smokeTestSourceSet.groovy.srcDirs
        testResourceDirs = testResourceDirs + smokeTestSourceSet.resources.srcDirs
        scopes["TEST"]!!["plus"]!!.add(smokeTestCompileClasspath)
        scopes["TEST"]!!["plus"]!!.add(smokeTestRuntimeClasspath)
    }
}
