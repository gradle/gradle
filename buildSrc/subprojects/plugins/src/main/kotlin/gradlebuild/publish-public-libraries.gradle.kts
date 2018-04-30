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
package gradlebuild

import accessors.base
import accessors.groovy
import accessors.java

import groovy.lang.MissingPropertyException
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.plugins.publish.GeneratePom
import org.gradle.plugins.publish.createArtifactPattern

import java.util.*

plugins {
    `maven`
}

val generatePom by tasks.creating(GeneratePom::class)

val main by java.sourceSets
val sourceJar by tasks.creating(Jar::class) {
    classifier = "sources"
    from(main.java.srcDirs + main.groovy.srcDirs)
}

configureUploadArchivesTask(generatePom)

artifacts {

    fun publishRuntime(artifact: Any) =
        add(generatePom.publishRuntime.name, artifact)

    publishRuntime(tasks["jar"])
    publishRuntime(sourceJar)
    publishRuntime(
        DefaultPublishArtifact(
            base.archivesBaseName,
            "pom",
            "pom",
            null,
            Date(),
            generatePom.pomFile,
            generatePom))
}

fun Project.configureUploadArchivesTask(generatePom: GeneratePom) {
    tasks.getByName<Upload>("uploadArchives") {
        // TODO Add magic property to upcoming configuration interface
        onlyIf { !project.hasProperty("noUpload") }
        configuration = generatePom.publishRuntime
        dependsOn(generatePom)
        isUploadDescriptor = false

        // TODO Remove once task configuration on demand is available and we can enforce properties at task configuration time
        failEarlyIfCredentialsAreNotSet(this)

        repositories {
            ivy {
                artifactPattern(createArtifactPattern(rootProject.extra.get("isSnapshot") as Boolean, project.group.toString(), base.archivesBaseName))
                credentials {
                    username = artifactoryUserName
                    password = artifactoryUserPassword
                }
            }
        }
    }
}

fun Project.failEarlyIfCredentialsAreNotSet(upload: Upload) {
    gradle.taskGraph.whenReady({
        if (hasTask(upload)) {
            if (artifactoryUserName.isNullOrEmpty()) {
                throw MissingPropertyException("artifactoryUserName is not set!")
            }
            if (artifactoryUserPassword.isNullOrEmpty()) {
                throw MissingPropertyException("artifactoryUserPassword is not set!")
            }
        }
    })
}

// TODO Add magic property to upcoming configuration interface
val Project.artifactoryUserName
    get() = findProperty("artifactoryUserName") as String?

// TODO Add magic property to upcoming configuration interface
val Project.artifactoryUserPassword
    get() = findProperty("artifactoryUserPassword") as String?


