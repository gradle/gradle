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
    def "resolving a @pom artifact from an included build replacing an external library fails the build"() {
        given:
        def mainProjectDir = file("main-project")
        def includedLoggingProjectDir = file("included-logging")

        mainProjectDir.file("settings.gradle.kts").text = """
            rootProject.name = "main-project"
            include("app")
            includeBuild("../included-logging")
        """

        mainProjectDir.file("app/build.gradle.kts").text = """
            plugins {
                application
            }

            dependencies {
                implementation("org.gradle.repro:lib@pom")
            }
        """

        includedLoggingProjectDir.file("settings.gradle.kts").text = """
            rootProject.name = "included-logging"
            include("lib")
        """

        includedLoggingProjectDir.file("lib/build.gradle.kts").text = """
            plugins {
                `java-library`
            }
        """

        includedLoggingProjectDir.file("gradle.properties").text = """
            group=org.gradle.repro
            version=0.1.0-SNAPSHOT
        """

        executer.inDirectory(mainProjectDir)

        expect:
        fails "build"
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':app:runtimeClasspath'")
        failure.assertHasCause("Could not find lib.pom (project :included-logging:lib)")
    }

    def "getting the file for a @pom artifact from an included build replacing an external library doesn't fail the build"() {
        given:
        def mainProjectDir = file("main-project")
        def includedLoggingProjectDir = file("included-logging")

        mainProjectDir.file("settings.gradle.kts").text = """
            rootProject.name = "main-project"
            include("app")
            includeBuild("../included-logging")
        """

        mainProjectDir.file("app/build.gradle.kts").text = """
            plugins {
                application
            }

            dependencies {
                implementation("org.gradle.repro:lib@pom")
            }

            tasks.register("resolve") {
                doLast {
                    val c: Configuration = configurations.getByName("compileClasspath")
                    c.getResolvedConfiguration()
                        .getLenientConfiguration()
                        .getAllModuleDependencies()
                        .map { it.getAllModuleArtifacts() }
                        .forEach { mas ->
                            mas.forEach { a ->
                                println(a.getFile())
                            }
                        }
                }
            }
        """

        includedLoggingProjectDir.file("settings.gradle.kts").text = """
            rootProject.name = "included-logging"
            include("lib")
        """

        includedLoggingProjectDir.file("lib/build.gradle.kts").text = """
            plugins {
                `java-library`
            }
        """

        includedLoggingProjectDir.file("gradle.properties").text = """
            group=org.gradle.repro
            version=0.1.0-SNAPSHOT
        """

        executer.inDirectory(mainProjectDir)

        expect:
        succeeds "resolve"
    }
}
