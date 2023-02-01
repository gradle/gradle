/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class LazyPublishArtifactIntegrationTest extends AbstractIntegrationSpec {
    def "exception querying the mapped value before task has completed"() {
        given:
        settingsKotlinFile << """
            include("dist")
        """

        and:
        buildKotlinFile << """
            plugins {
                `java-library`
            }

            val zips by configurations.creating {
                isCanBeResolved = true
                isCanBeConsumed = false
            }

            dependencies {
                zips(project(":dist", "zips"))
            }

            val printDistFiles by tasks.registering {
                inputs.files(zips)
                doLast {
                    val names = zips.map { it.name }.sorted()
                    val expected = listOf("buildZip.zip", "buildZip.zip.sha512")
                    require(names == expected) {
                        "expected: \$expected, got: \$names"
                    }
                }
            }
        """

        and:
        file("dist/build.gradle.kts") << """
            plugins {
                `java-library`
            }

            val zips by configurations.creating {
                isCanBeConsumed = true
                isCanBeResolved = false
            }

            dependencies {
                // This dependency breaks :dist:test task
                implementation(project(":"))
            }

            // Here we generate a zip file along with its checksum and share it via zips configuration with the root project
            val buildZip by tasks.registering(Zip::class) {
                archiveBaseName.set("buildZip")
                archiveVersion.set("")
                from("build.gradle.kts")
            }

            artifacts {
                add(zips.name, buildZip)
            }

            val archiveFile = buildZip.flatMap { it.archiveFile }
            // ORIGINAL
            //val sha512File = archiveFile.map { File(it.asFile.absolutePath + ".sha512") }
            /// FIXED
            val sha512File = archiveFile.flatMap { project.provider { File(it.asFile.absolutePath + ".sha512") } }
            val shaTask = project.tasks.register(buildZip.name + "Sha512") {
                onlyIf { archiveFile.get().asFile.exists() }
                inputs.file(archiveFile)
                outputs.file(sha512File)
                doLast {
                    ant.withGroovyBuilder {
                        "checksum"(
                            "file" to archiveFile.get(),
                            "algorithm" to "SHA-512",
                            "fileext" to ".sha512",
                            // Make the files verifiable with shasum -c *.sha512
                            "format" to "MD5SUM"
                        )
                    }
                }
            }
            artifacts {
                // https://github.com/gradle/gradle/issues/10960
                add(zips.name, sha512File) {
                    type = "sha512"
                    builtBy(shaTask)
                }
            }

            project.tasks.named(BasePlugin.ASSEMBLE_TASK_NAME) {
                dependsOn(shaTask)
            }
        """

        expect:
        succeeds("printDistFiles")
    }

    def "potential workaround providing all coordinates when adding artifact still causes exception querying the mapped value before task has completed"() {
        given:
        settingsKotlinFile << """
            include("dist")
        """

        and:
        buildKotlinFile << """
            plugins {
                `java-library`
            }

            val zips by configurations.creating {
                isCanBeResolved = true
                isCanBeConsumed = false
            }

            dependencies {
                zips(project(":dist", "zips"))
            }

            val printDistFiles by tasks.registering {
                inputs.files(zips)
                doLast {
                    val names = zips.map { it.name }.sorted()
                    val expected = listOf("buildZip.zip", "buildZip.zip.sha512")
                    require(names == expected) {
                        "expected: \$expected, got: \$names"
                    }
                }
            }
        """

        and:
        file("dist/build.gradle.kts") << """
            plugins {
                `java-library`
            }

            val zips by configurations.creating {
                isCanBeConsumed = true
                isCanBeResolved = false
            }

            dependencies {
                // This dependency breaks :dist:test task
                implementation(project(":"))
            }

            // Here we generate a zip file along with its checksum and share it via zips configuration with the root project
            val buildZip by tasks.registering(Zip::class) {
                archiveBaseName.set("buildZip")
                archiveVersion.set("")
                from("build.gradle.kts")
            }

            artifacts {
                add(zips.name, buildZip)
            }

            val archiveFile = buildZip.flatMap { it.archiveFile }
            // ORIGINAL
            //val sha512File = archiveFile.map { File(it.asFile.absolutePath + ".sha512") }
            /// FIXED
            val sha512File = archiveFile.flatMap { project.provider { File(it.asFile.absolutePath + ".sha512") } }
            val shaTask = project.tasks.register(buildZip.name + "Sha512") {
                onlyIf { archiveFile.get().asFile.exists() }
                inputs.file(archiveFile)
                outputs.file(sha512File)
                doLast {
                    ant.withGroovyBuilder {
                        "checksum"(
                            "file" to archiveFile.get(),
                            "algorithm" to "SHA-512",
                            "fileext" to ".sha512",
                            // Make the files verifiable with shasum -c *.sha512
                            "format" to "MD5SUM"
                        )
                    }
                }
            }
            artifacts {
                add(zips.name, sha512File) {
                    // WA for https://github.com/gradle/gradle/issues/16777
                    // The exact values do not seem to be really important, are they?
                    name = "testfile"
                    classifier = ""
                    extension = "sha512"
                    // https://github.com/gradle/gradle/issues/10960
                    type = "sha512"
                    builtBy(shaTask)
                }
            }

            project.tasks.named(BasePlugin.ASSEMBLE_TASK_NAME) {
                dependsOn(shaTask)
            }
        """

        expect:
        succeeds("printDistFiles", "--console=verbose")
    }
}
