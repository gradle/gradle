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
import org.gradle.gradlebuild.unittestandcompile.ModuleType
import org.gradle.gradlebuild.test.integrationtests.SmokeTest

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
    smokeTestImplementation(library("commons_io"))
    smokeTestImplementation(library("jgit"))
    smokeTestImplementation(testLibrary("spock"))

    smokeTestRuntimeOnly(project(":kotlinDsl"))
    smokeTestRuntimeOnly(project(":codeQuality"))
    smokeTestRuntimeOnly(project(":ide"))
    smokeTestRuntimeOnly(project(":ivy"))
    smokeTestRuntimeOnly(project(":jacoco"))
    smokeTestRuntimeOnly(project(":maven"))
    smokeTestRuntimeOnly(project(":plugins"))
    smokeTestRuntimeOnly(project(":pluginDevelopment"))
    smokeTestRuntimeOnly(project(":toolingApiBuilders"))
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

testFixtures {
    from(":core")
    from(":versionControl")
}

tasks.named<Copy>("processSmokeTestResources").configure {
    from("$rootDir/gradle/init-scripts") {
        into("org/gradle/smoketests/cache-init-scripts")
        include("overlapping-task-outputs-stats-init.gradle")
    }
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
