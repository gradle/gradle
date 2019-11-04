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
import org.gradle.gradlebuild.test.integrationtests.SmokeTest
import org.gradle.gradlebuild.test.integrationtests.defaultGradleGeneratedApiJarCacheDirProvider
import org.gradle.gradlebuild.unittestandcompile.ModuleType

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
    smokeTestImplementation(project(":persistentCache"))
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
    gradleInstallationForTest.gradleGeneratedApiJarCacheDir.set(
        defaultGradleGeneratedApiJarCacheDirProvider()
    )
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
