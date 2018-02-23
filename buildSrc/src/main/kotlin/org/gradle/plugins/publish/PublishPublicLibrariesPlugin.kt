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
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer
import org.gradle.api.artifacts.maven.MavenResolver
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.tasks.Upload
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import java.io.File
import java.util.*

open class PublishPublicLibrariesPlugin : Plugin<Project> {

    override
    fun apply(project: Project): Unit = project.run {
        apply {
            plugin("maven")
        }

        val publishCompile by configurations.creating
        val publishRuntime by configurations.creating
        val compile by configurations.getting {
            extendsFrom(publishCompile)
        }
        lateinit var pomFile: File

        val generatePom by tasks.creating {
            pomFile = File(temporaryDir, "pom.xml")
            extra.set("pomFile", pomFile)

            doLast {
                dependencies {
                    publishCompile.allDependencies.withType<ProjectDependency>().forEach {
                        publishRuntime("org.gradle:${it.dependencyProject.base.archivesBaseName}:${version}")
                    }
                    publishCompile.allDependencies.withType<ExternalDependency>().forEach {
                        publishRuntime(it)
                    }
                }

                val install by tasks.getting(Upload::class)
                val mavenInstaller by install.repositories

                (mavenInstaller as MavenResolver).apply {
                    pom.scopeMappings.mappings.clear()
                    pom.scopeMappings.addMapping(300, publishRuntime, Conf2ScopeMappingContainer.RUNTIME)
                    pom.groupId = project.group.toString()
                    pom.artifactId = base.archivesBaseName
                    pom.version = project.version.toString()
                    pom.writeTo(pomFile)
                }
            }
        }

        val sourceJar by tasks.creating(Jar::class) {
            classifier = "sources"
            val main by java.sourceSets
            from(main.java.srcDirs + main.groovy.srcDirs)
        }

        tasks {
            val uploadArchives by getting(Upload::class) {
                onlyIf { !project.hasProperty("noUpload") }

                var artifactoryUserName: String? = null
                var artifactoryUserPassword: String? = null
                gradle.taskGraph.whenReady(Action<TaskExecutionGraph> {
                    if (hasTask(this@getting)) {
                        // check properties defined and fail early
                        artifactoryUserName = project.property("artifactoryUserName") as String
                        artifactoryUserPassword = project.property("artifactoryUserPassword") as String
                    }
                })

                configuration = publishRuntime
                dependsOn(generatePom)
                isUploadDescriptor = false
                doFirst {
                    repositories {
                        ivy {
                            // TODO Refactor eventually versioning.gradle that isSnapshot is not stored as an extra property
                            val libsType = if ((project.extra.get("isSnapshot") as Boolean)) "snapshots" else "releases"
                            val repo = "https://gradle.artifactoryonline.com/gradle/libs-$libsType-local"
                            artifactPattern("${repo}/${project.group.toString().replace("\\.", "/")}/${base.archivesBaseName}/[revision]/[artifact]-[revision](-[classifier]).[ext]")
                            credentials {
                                username = artifactoryUserName
                                password = artifactoryUserPassword
                            }
                        }
                    }
                }
            }
        }

        artifacts {
            add(publishRuntime.name, tasks["jar"])
            add(publishRuntime.name, sourceJar)
            add(publishRuntime.name, DefaultPublishArtifact(base.archivesBaseName, "pom", "pom", null, Date(), pomFile, generatePom))
        }

    }

}
