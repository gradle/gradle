/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import accessors.groovy
import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.gradlebuild.test.integrationtests.SmokeTest
import org.gradle.gradlebuild.test.integrationtests.defaultGradleGeneratedApiJarCacheDirProvider
import org.gradle.gradlebuild.unittestandcompile.ModuleType
import org.gradle.gradlebuild.versioning.DetermineCommitId
import org.gradle.testing.performance.generator.tasks.RemoteProject

plugins {
    `java-library`
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

val smokeTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

val smokeTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val smokeTestRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}
val smokeTestCompileClasspath: Configuration by configurations.getting
val smokeTestRuntimeClasspath: Configuration by configurations.getting

configurations {
    partialDistribution.get().extendsFrom(
        get(smokeTest.runtimeClasspathConfigurationName)
    )
}

dependencies {
    smokeTestImplementation(project(":baseServices"))
    smokeTestImplementation(project(":coreApi"))
    smokeTestImplementation(project(":testKit"))
    smokeTestImplementation(project(":internalIntegTesting"))
    smokeTestImplementation(project(":launcher"))
    smokeTestImplementation(project(":persistentCache"))
    smokeTestImplementation(project(":jvmServices"))
    smokeTestImplementation(library("commons_io"))
    smokeTestImplementation(library("jgit"))
    smokeTestImplementation(library("gradleProfiler")) {
        because("Using build mutators to change a Java file")
    }
    smokeTestImplementation(testLibrary("spock"))

    val allTestRuntimeDependencies: DependencySet by rootProject.extra
    allTestRuntimeDependencies.forEach {
        smokeTestRuntimeOnly(it)
    }

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":versionControl")))
}

fun SmokeTest.configureForSmokeTest() {
    group = "Verification"
    testClassesDirs = smokeTest.output.classesDirs
    classpath = smokeTest.runtimeClasspath
    maxParallelForks = 1 // those tests are pretty expensive, we shouldn"t execute them concurrently
    gradleInstallationForTest.gradleGeneratedApiJarCacheDir.set(
        defaultGradleGeneratedApiJarCacheDirProvider(rootProject.providers, rootProject.layout)
    )
}

tasks.register<SmokeTest>("smokeTest") {
    description = "Runs Smoke tests"
    configureForSmokeTest()
}

tasks.register<SmokeTest>("instantSmokeTest") {
    description = "Runs Smoke tests with instant execution"
    configureForSmokeTest()
    systemProperty("org.gradle.integtest.executer", "instant")
}

plugins.withType<IdeaPlugin>().configureEach {
    model.module {
        testSourceDirs = testSourceDirs + smokeTest.groovy.srcDirs
        testResourceDirs = testResourceDirs + smokeTest.resources.srcDirs
        scopes["TEST"]!!["plus"]!!.add(smokeTestCompileClasspath)
        scopes["TEST"]!!["plus"]!!.add(smokeTestRuntimeClasspath)
    }
}

tasks {

    /**
     * Santa Tracker git URI.
     *
     * Note that you can change it to `file:///path/to/your/santa-tracker-clone/.git`
     * if you need to iterate quickly on changes to Santa Tracker.
     */
    val santaGitUri = "https://github.com/gradle/santa-tracker-android.git"

    register<RemoteProject>("santaTrackerKotlin") {
        remoteUri.set(santaGitUri)
        // Pinned from branch agp-3.6.0
        ref.set("65479d5a244a64afef79d86b4bbc81d8908d2434")
    }

    register<RemoteProject>("santaTrackerJava") {
        remoteUri.set(santaGitUri)
        // Pinned from branch agp-3.6.0-java
        ref.set("5fff06e2496cc762b34031f6dd28467041ae8453")
    }

    register<RemoteProject>("gradleBuildCurrent") {
        remoteUri.set(rootDir.absolutePath)
        ref.set(rootProject.tasks.named<DetermineCommitId>("determineCommitId").flatMap { it.determinedCommitId })
    }

    val remoteProjects = withType<RemoteProject>()

    if (BuildEnvironment.isCiServer) {
        remoteProjects.configureEach {
            outputs.upToDateWhen { false }
        }
    }

    register<Delete>("cleanRemoteProjects") {
        delete(remoteProjects.map { it.outputDirectory })
    }

    withType<SmokeTest>().configureEach {
        dependsOn(remoteProjects)
        inputs.property("androidHomeIsSet", System.getenv("ANDROID_HOME") != null)
    }
}
