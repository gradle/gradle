/*
 * Copyright 2024 the original author or authors.
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

import com.gradleup.gr8.EmbeddedJarTask
import com.gradleup.gr8.Gr8Task
import gradlebuild.basics.launcherDebuggingIsEnabled
import java.util.jar.Attributes

plugins {
    id("gradlebuild.distribution.api-java")
    id("com.gradleup.gr8") version "0.10"
}

description = "Entry point of the Gradle wrapper command"

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(projects.cli)
    implementation(projects.wrapperShared)

    testImplementation(projects.baseServices)
    testImplementation(testFixtures(projects.core))

    integTestImplementation(projects.buildOption)
    integTestImplementation(projects.launcher)
    integTestImplementation(projects.logging)
    integTestImplementation(projects.coreApi)
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.littleproxy)
    integTestImplementation(libs.jetty)

    crossVersionTestImplementation(projects.logging)
    crossVersionTestImplementation(projects.persistentCache)
    crossVersionTestImplementation(projects.launcher)

    integTestNormalizedDistribution(projects.distributionsFull)
    crossVersionTestNormalizedDistribution(projects.distributionsFull)

    integTestDistributionRuntimeOnly(projects.distributionsFull)
    crossVersionTestDistributionRuntimeOnly(projects.distributionsFull)
}

val executableJar by tasks.registering(Jar::class) {
    archiveFileName = "gradle-wrapper-executable.jar"
    manifest {
        attributes.remove(Attributes.Name.IMPLEMENTATION_VERSION.toString())
        attributes(Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle Wrapper")
    }
    from(layout.projectDirectory.dir("src/executable/resources"))
    from(sourceSets.main.get().output)
    // Exclude properties files from this project as they are not needed for the executable JAR
    exclude("gradle-*-classpath.properties")
    exclude("gradle-*-parameter-names.properties")
}

// Using Gr8 plugin with ProGuard to minify the wrapper JAR.
// This minified JAR is added to the project root when the wrapper task is used.
// It is embedded in the main JAR as a resource called `/gradle-wrapper.jar.`
gr8 {
    create("gr8") {
        // TODO This should work by passing `executableJar` directly to th Gr8 plugin
        programJar(executableJar.flatMap { it.archiveFile })
        archiveName("gradle-wrapper.jar")
        configuration("runtimeClasspath")
        proguardFile("src/main/proguard/wrapper.pro")
        // Exclude META-INF resources from Guava etc. added via transitive dependencies
        exclude("META-INF/.*")
        // Exclude properties files from dependency subprojects
        exclude("gradle-.*-classpath.properties")
        exclude("gradle-.*-parameter-names.properties")
    }
}

// TODO This dependency should be configured by the Gr8 plugin
tasks.named<EmbeddedJarTask>("gr8EmbeddedJar") {
    dependsOn(executableJar)
}

// https://github.com/gradle/gradle/issues/26658
// Before introducing gr8, wrapper jar is generated as build/libs/gradle-wrapper.jar and used in promotion build
// After introducing gr8, wrapper jar is generated as build/libs/gradle-wrapper-executable.jar and processed
//   by gr8, then the processed `gradle-wrapper.jar` need to be copied back to build/libs for promotion build
val copyGr8OutputJarAsGradleWrapperJar by tasks.registering(Copy::class) {
    from(tasks.named<Gr8Task>("gr8R8Jar").flatMap { it.outputJar() })
    into(layout.buildDirectory.dir("libs"))
}

val debuggableJar by tasks.registering(Jar::class) {
    archiveFileName = "gradle-wrapper.jar"
    from(executableJar.map { it.source })
    from(configurations.runtimeClasspath.get().incoming.artifactView {
        attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.CLASSES))
    }.files)
}

tasks.jar {
    if (launcherDebuggingIsEnabled) { // shadowing and minification prevents debugging
        from(debuggableJar)
    } else {
        from(tasks.named<Gr8Task>("gr8R8Jar").flatMap { it.outputJar() })
        dependsOn(copyGr8OutputJarAsGradleWrapperJar)
    }
}

tasks.isolatedProjectsIntegTest {
    enabled = false
}
