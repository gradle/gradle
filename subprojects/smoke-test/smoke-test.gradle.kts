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
import org.gradle.gradlebuild.unittestandcompile.ModuleType
import org.gradle.gradlebuild.versioning.DetermineCommitId
import org.gradle.testing.performance.generator.tasks.RemoteProject

plugins {
    `java-library`
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
    smokeTestImplementation(library("commons_io"))
    smokeTestImplementation(library("jgit"))
    smokeTestImplementation(testLibrary("spock"))

    val allTestRuntimeDependencies: DependencySet by rootProject.extra
    allTestRuntimeDependencies.forEach {
        smokeTestRuntimeOnly(it)
    }

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":versionControl")))
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

tasks.register<SmokeTest>("smokeTest") {
    group = "Verification"
    description = "Runs Smoke tests"
    testClassesDirs = smokeTest.output.classesDirs
    classpath = smokeTest.runtimeClasspath
    maxParallelForks = 1 // those tests are pretty expensive, we shouldn"t execute them concurrently
}

plugins.withType<IdeaPlugin>().configureEach { // lazy as plugin not applied yet
    model.module {
        testSourceDirs = testSourceDirs + smokeTest.groovy.srcDirs
        testResourceDirs = testResourceDirs + smokeTest.resources.srcDirs
        scopes["TEST"]!!["plus"]!!.add(smokeTestCompileClasspath)
        scopes["TEST"]!!["plus"]!!.add(smokeTestRuntimeClasspath)
    }
}

plugins.withType<EclipsePlugin>().configureEach { // lazy as plugin not applied yet
    eclipse.classpath {
        plusConfigurations.add(smokeTestCompileClasspath)
        plusConfigurations.add(smokeTestRuntimeClasspath)
    }
}

tasks {
    // TODO Copied from instant-execution.gradle.kts, we should have one place to clone this thing and clone it from there locally when needed
    val santaTracker by registering(RemoteProject::class) {
        remoteUri.set("https://github.com/gradle/santa-tracker-android.git")
        // From branch agp-3.6.0
        ref.set("036aad22af993d2f564a6a15d6a7b9706ba37d8e")
    }

    val gradleBuildCurrent by registering(RemoteProject::class) {
        remoteUri.set(rootDir.absolutePath)
        ref.set(rootProject.tasks.named<DetermineCommitId>("determineCommitId").flatMap { it.determinedCommitId })
    }

    if (BuildEnvironment.isCiServer) {
        withType<RemoteProject>().configureEach {
            outputs.upToDateWhen { false }
        }
    }

    withType<SmokeTest>().configureEach {
        dependsOn(santaTracker)
        dependsOn(gradleBuildCurrent)
        inputs.property("androidHomeIsSet", System.getenv("ANDROID_HOME") != null)
        inputs.property("gradleBuildJavaHomeIsSet", System.getenv("GRADLE_BUILD_JAVA_HOME") != null)
    }

    register<Delete>("cleanRemoteProjects") {
        delete(santaTracker.get().outputDirectory)
    }
}
