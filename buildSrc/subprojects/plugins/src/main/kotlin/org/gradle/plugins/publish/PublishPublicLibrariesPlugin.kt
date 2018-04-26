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
package org.gradle.plugins.publish

import accessors.base
import accessors.groovy
import accessors.java
import groovy.lang.MissingPropertyException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.tasks.Upload
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.*
import java.util.*


open class PublishPublicLibrariesPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        apply {
            plugin("maven")
        }

        val generatePom by tasks.creating(GeneratePom::class)

        val sourceJar by tasks.creating(Jar::class) {
            classifier = "sources"
            from(main.java.srcDirs + main.groovy.srcDirs)
        }

        configureUploadArchivesTask(generatePom)

        artifacts {

            generatePom.publishRuntime.name.let { publishRuntime ->
                add(publishRuntime, tasks["jar"])
                add(publishRuntime, sourceJar)
                add(publishRuntime,
                    DefaultPublishArtifact(
                        base.archivesBaseName,
                        "pom",
                        "pom",
                        null,
                        Date(),
                        generatePom.pomFile,
                        generatePom))
            }
        }
    }

    private
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

    private
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
    private
    val Project.artifactoryUserName
        get() = findProperty("artifactoryUserName") as String?

    // TODO Add magic property to upcoming configuration interface
    private
    val Project.artifactoryUserPassword
        get() = findProperty("artifactoryUserPassword") as String?

    private
    val Project.main
        get() = java.sourceSets["main"]
}


const val GRADLE_REPO = "https://gradle.artifactoryonline.com/gradle"


fun createArtifactPattern(isSnapshot: Boolean, group: String, artifactName: String): String {
    assert(group.isNotEmpty())
    assert(artifactName.isNotEmpty())

    val libsType = if (isSnapshot) "snapshots" else "releases"
    val repoUrl = "https://gradle.artifactoryonline.com/gradle/libs-$libsType-local"
    val groupId = group.toString().replace(".", "/")
    return "$repoUrl/$groupId/$artifactName/[revision]/[artifact]-[revision](-[classifier]).[ext]"
}
