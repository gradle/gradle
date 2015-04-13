/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.tasks.diagnostics

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class BuildscriptDependencyReportTaskIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.requireOwnGradleUserHomeDir()
    }

    def "reports external dependency name and version change"() {
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").publish()
        mavenRepo.module("org", "leaf3").publish()
        mavenRepo.module("org", "leaf4").publish()

        mavenRepo.module("org", "toplevel1").dependsOn('leaf1', 'leaf2').publish()
        mavenRepo.module("org", "toplevel2").dependsOn('leaf3', 'leaf4').publish()

        file("settings.gradle") << "include 'client', 'impl'"

        buildFile << """
            buildscript {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                dependencies {
                    classpath 'org:toplevel1:1.0'
                }
            }

            project(":impl") {
                buildscript {
                    repositories {
                        maven { url "${mavenRepo.uri}" }
                    }
                    dependencies {
                        classpath 'org:toplevel2:1.0'
                    }
                }

                configurations {
                    config1
                }
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                dependencies {
                    config1 'org:leaf1:1.0'
                }
            }
"""

        when:
        run(":impl:buildscriptDependencies")

        then:
        output.contains(toPlatformLineSeparators("""
classpath
\\--- org:toplevel2:1.0
     +--- org:leaf3:1.0
     \\--- org:leaf4:1.0
"""))
        when:
        run(":client:buildscriptDependencies")

        then:
        output.contains(toPlatformLineSeparators("""
classpath
No dependencies
"""))

        when:
        run(":buildscriptDependencies")

        then:
        output.contains(toPlatformLineSeparators("""
classpath
\\--- org:toplevel1:1.0
     +--- org:leaf1:1.0
     \\--- org:leaf2:1.0
"""))
    }
}