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
import java.util.jar.Attributes

plugins {
    gradlebuild.distribution.`api-java`
}

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":cli"))

    testImplementation(project(":baseServices"))
    testImplementation(project(":native"))
    testImplementation(library("ant"))
    testImplementation(testFixtures(project(":core")))

    integTestImplementation(project(":logging"))
    integTestImplementation(project(":coreApi"))
    integTestImplementation(library("commons_io"))
    integTestImplementation(testLibrary("littleproxy"))
    integTestImplementation(testLibrary("jetty"))

    crossVersionTestImplementation(project(":logging"))
    crossVersionTestImplementation(project(":persistentCache"))
    crossVersionTestImplementation(project(":launcher"))

    integTestNormalizedDistribution(project(":distributionsFull"))
    crossVersionTestNormalizedDistribution(project(":distributionsFull"))

    integTestDistributionRuntimeOnly(project(":distributionsFull"))
    crossVersionTestDistributionRuntimeOnly(project(":distributionsFull"))
}

val executableJar by tasks.registering(Jar::class) {
    archiveFileName.set("gradle-wrapper.jar")
    manifest {
        attributes.remove(Attributes.Name.IMPLEMENTATION_VERSION.toString())
        attributes(Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle Wrapper")
    }
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().incoming.artifactView {
        attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.CLASSES))
    }.files)
}

tasks.jar.configure {
    from(executableJar)
}

// === TODO remove and address the following when we have a good reason to change the wrapper jar
executableJar.configure {
    val cliClasspath = layout.buildDirectory.file("gradle-cli-classpath.properties") // This file was accidentally included into the gradle-wrapper.jar
    val cliParameterNames = layout.buildDirectory.file("gradle-cli-parameter-names.properties")  // This file was accidentally included into the gradle-wrapper.jar
    doFirst {
        cliClasspath.get().asFile.writeText("projects=\nruntime=\n")
        cliParameterNames.get().asFile.writeText("")
    }
    from(cliClasspath)
    from(cliParameterNames)
}
strictCompile {
    ignoreRawTypes() // Raw type used in 'org.gradle.wrapper.Install', fix this or add an ignore/suppress annotation there
}
// ===
