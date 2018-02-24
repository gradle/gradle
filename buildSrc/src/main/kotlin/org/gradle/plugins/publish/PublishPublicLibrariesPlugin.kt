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
import accessors.java
import accessors.groovy
import groovy.lang.MissingPropertyException
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.plugins.MavenRepositoryHandlerConvention
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Upload
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import java.io.File
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

        val uploadArchives by tasks.getting(Upload::class) {
            // TODO Add magic property to upcoming configuration interface
            onlyIf { !project.hasProperty("noUpload") }
            configuration = generatePom.publishRuntime
            dependsOn(generatePom)
            isUploadDescriptor = false

            // TODO Remove once task configuration on demand is available and we can enforce properties at task configuration time
            failEarlyIfCredentialsAreNotSet(this)

            repositories {
                ivy {
                    artifactPattern("$repoUrl/$groupId/${base.archivesBaseName}/[revision]/[artifact]-[revision](-[classifier]).[ext]")
                    credentials {
                        username = artifactoryUserName
                        password = artifactoryUserPassword
                    }
                }
            }
        }

        artifacts {
            add(generatePom.publishRuntime.name, tasks["jar"])
            add(generatePom.publishRuntime.name, sourceJar)
            add(generatePom.publishRuntime.name, DefaultPublishArtifact(base.archivesBaseName,
                "pom",
                "pom",
                null,
                Date(),
                generatePom.pomFile,
                generatePom))
        }

    }

    private
    val Project.repoUrl: String
        get() {
            // TODO Refactor versioning.gradle that isSnapshot is not stored as an extra property
            val libsType = if ((rootProject.extra.get("isSnapshot") as Boolean)) "snapshots" else "releases"
            return "https://gradle.artifactoryonline.com/gradle/libs-$libsType-local"
        }

    private
    val Project.groupId: String
        get() = group.toString().replace("\\.", "/")

    private fun Project.failEarlyIfCredentialsAreNotSet(upload: Upload) {
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

open class GeneratePom : DefaultTask() {
    @InputFile
    val pomFile = File(temporaryDir, "pom.xml")

    // TODO How to annotate?
    val publishCompile by project.configurations.creating

    // TODO How to annotate?
    val publishRuntime by project.configurations.creating

    init {
        // Subprojects assign dependencies to publishCompile to indicate that they should be part of the published pom.
        // Therefore compile needs to contain those dependencies and extend publishCompile
        val compile by project.configurations.getting {
            extendsFrom(publishCompile)
        }
    }

    @TaskAction
    fun generatePom(): Unit = project.run {
        val install by tasks.getting(Upload::class)
        install.repositories {
            withConvention(MavenRepositoryHandlerConvention::class) {
                mavenInstaller {
                    pom {
                        scopeMappings.mappings.clear()
                        scopeMappings.addMapping(300, publishRuntime, Conf2ScopeMappingContainer.RUNTIME)
                        groupId = project.group.toString()
                        artifactId = base.archivesBaseName
                        version = project.version.toString()
                        writeTo(pomFile)
                    }
                }
            }
        }
    }

    private
    fun Project.addDependenciesToPublishConfigurations() {
        dependencies {
            publishCompile.allDependencies.withType<ProjectDependency>().forEach {
                publishRuntime("org.gradle:${it.dependencyProject.base.archivesBaseName}:$version")
            }
            publishCompile.allDependencies.withType<ExternalDependency>().forEach {
                publishRuntime(it)
            }
        }
    }
}



