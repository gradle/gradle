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
import accessors.java
import accessors.publishing

import codegen.GenerateClasspathManifest

/*
 * Configures a Gradle Kotlin DSL module for publication to artifactory.
 *
 * The published jar will:
 *  - be named after `base.archivesBaseName`
 *  - include all sources
 *  - contain a classpath manifest
 */

apply(plugin = "kotlin-dsl-module")
apply(plugin = "maven-publish")
apply(plugin = "com.jfrog.artifactory")

// with a jar named after `base.archivesBaseName`
publishing {
    publications.create<MavenPublication>("mavenJava") {
        artifactId = base.archivesBaseName
        from(components["java"])
    }
}

tasks.getByName("artifactoryPublish") {
    dependsOn("jar")
}

// classpath manifest
val generatedResourcesDir = file("$buildDir/generate-resources/main")
val generateClasspathManifest by tasks.creating(GenerateClasspathManifest::class) {
    outputDirectory = generatedResourcesDir
}
val main by java.sourceSets
main.output.dir(
    mapOf("builtBy" to generateClasspathManifest),
    generatedResourcesDir)
