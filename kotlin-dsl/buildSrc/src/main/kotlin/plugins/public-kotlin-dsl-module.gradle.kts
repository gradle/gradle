/*
 * Copyright 2018 the original author or authors.
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

import accessors.base
import accessors.publishing
import accessors.sourceSets

import codegen.GenerateClasspathManifest

/*
 * Configures a Gradle Kotlin DSL module for publication to repo.gradle.org.
 *
 * The published jar will:
 *  - be named after `base.archivesBaseName`
 *  - include all sources
 *  - contain a classpath manifest
 */

plugins {
    id("kotlin-dsl-module")
    `maven-publish`
}

afterEvaluate {
    publishing {

        // with a jar named after `base.archivesBaseName`
        publications {
            create<MavenPublication>("mavenJava") {
                artifactId = base.archivesBaseName
                from(components["java"])
            }
        }

        repositories {
            val targetRepoKey = "libs-${buildTagFor(project.version as String)}s-local"
            maven(url = "https://repo.gradle.org/gradle/$targetRepoKey") {
                authentication {
                    credentials {

                        fun stringProperty(name: String): String? = project.findProperty(name) as? String

                        username = stringProperty("artifactory_user") ?: "nouser"
                        password = stringProperty("artifactory_password") ?: "nopass"
                    }
                }
            }
        }
    }
}

// classpath manifest
val generatedResourcesDir = file("$buildDir/generate-resources/main")
val generateClasspathManifest by tasks.registering(GenerateClasspathManifest::class) {
    outputDirectory = generatedResourcesDir
}

sourceSets.named("main") {
    output.dir(generatedResourcesDir, "builtBy" to generateClasspathManifest)
}

fun buildTagFor(version: String): String =
    when (version.substringAfterLast('-')) {
        "SNAPSHOT" -> "snapshot"
        else -> "release"
    }
