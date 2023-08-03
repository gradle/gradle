/*
 * Copyright 2022 the original author or authors.
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
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/22875")
class ResolvePOMIntegrationTest extends AbstractIntegrationSpec {
    def mainProjectDir

    def setup() {
        mainProjectDir = file("main-project")
        mainProjectDir.file("settings.gradle").text = """
            rootProject.name = "main-project"
            include("app")
            includeBuild("../included-logging")
        """

        mainProjectDir.file("app/build.gradle").text = """
            plugins {
                id 'application'
            }

            dependencies {
                implementation("org.gradle.repro:lib@pom")
            }

            abstract class Resolve extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getArtifactFiles()

                @TaskAction
                void printThem() {
                    assert artifactFiles.size() == 0
                }
            }
        """

        def includedLoggingProjectDir = file("included-logging")

        includedLoggingProjectDir.file("settings.gradle").text = """
            rootProject.name = "included-logging"
            include("lib")
        """

        includedLoggingProjectDir.file("lib/build.gradle").text = """
            plugins {
                id 'java-library'
            }
        """

        includedLoggingProjectDir.file("gradle.properties").text = """
            group=org.gradle.repro
            version=0.1.0-SNAPSHOT
        """

        executer.inDirectory(mainProjectDir)
    }

    def "resolving a @pom artifact from an included build replacing an external library fails the build"() {
        given:
        mainProjectDir.file("app/build.gradle") << """
            tasks.register("resolve", Resolve) {
                artifactFiles.from(configurations.getByName("compileClasspath"))
            }
        """

        expect:
        fails "build"
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':app:runtimeClasspath'")
        failure.assertHasCause("Could not find lib.pom (project :included-logging:lib)")
    }

    def "resolving a @pom artifact from an included build replacing an external library does not fail the build with a lenient configuration"() {
        given:
        mainProjectDir.file("app/build.gradle") << """
            tasks.register("resolve", Resolve) {
                artifactFiles.from {
                    configurations.getByName("compileClasspath").getResolvedConfiguration()
                        .getLenientConfiguration()
                        .getFiles()
                }
            }
        """

        expect:
        succeeds "resolve"
    }

    def "resolving a @pom artifact from an included build replacing an external library does not fail the build with a lenient artifact view"() {
        given:
        mainProjectDir.file("app/build.gradle") << """
            tasks.register("resolve", Resolve) {
                artifactFiles.from(configurations.getByName("compileClasspath").incoming.artifactView {
                    lenient = true
                }.getFiles())
            }
        """

        expect:
        succeeds "resolve"
    }
}
