/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import spock.lang.Issue

@FluidDependenciesResolveTest
class ProjectDependenciesIntegrationTest extends AbstractDependencyResolutionTest {
    @Issue("GRADLE-2477") //this is a feature on its own but also covers one of the reported issues
    def "resolving project dependency triggers configuration of the target project"() {
        settingsFile << "include 'impl'"
        buildFile << """
            apply plugin: 'java'
            dependencies {
                implementation project(":impl")
            }
            repositories {
                //resolving project must declare the repo
                maven { url '${mavenRepo.uri}' }
            }
            println "Resolved at configuration time: " + configurations.runtimeClasspath.files*.name
        """

        mavenRepo.module("org", "foo").publish()
        file("impl/build.gradle") << """
            apply plugin: 'java'
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        succeeds()

        then:
        outputContains "Resolved at configuration time: [impl.jar, foo-1.0.jar]"
    }

    def "configuring project dependencies by map is validated"() {
        settingsFile << "include 'impl'"
        buildFile << """
            allprojects { configurations.create('conf') }
            task extraKey {
                doLast {
                    dependencies.project(path: ":impl", configuration: ":conf", foo: "bar")
                }
            }
            task missingPath {
                doLast {
                    dependencies.project(paths: ":impl", configuration: ":conf")
                }
            }
            task missingConfiguration {
                doLast {
                    dependencies.project(path: ":impl")
                }
            }
        """

        when:
        runAndFail("extraKey")

        then:
        failureHasCause("Could not set unknown property 'foo' for ")

        when:
        run("missingConfiguration")

        then:
        noExceptionThrown()

        when:
        runAndFail("missingPath")

        then:
        failureHasCause("Required keys [path] are missing from map")
    }

    @Issue("GRADLE-20377")
    def "if project dependency exists, GAV cannot be changed on referenced project; #explanation"() {
        given: "a project a, with a dep on project b:c, added prior to project b running a subprojects block to change project c's group"
        file('settings.gradle') << "include 'a', 'b:c', 'b'"

        testDirectory.file('a', 'build.gradle') << """
            plugins {
                id 'java-library'
            }

            group 'com.a'

            dependencies {
                implementation project(":b:c")
            }

            task checkCompileClasspath {
                doLast {
                    assert configurations.compileClasspath.files.size() == 1
                }
            }
        """.stripIndent()

        testDirectory.file('b', 'build.gradle') << """
            subprojects {
                group 'com.somethingelse'
            }
        """.stripIndent()

        testDirectory.file('b', 'c', 'build.gradle') << """
            plugins {
                id 'java-library'
            }

            group 'com.c'
        """.stripIndent()

        testDirectory.file('gradle.properties') << "org.gradle.configureondemand = $configureOnDemand"

        expect: "build should succeed or fail (with explanation that the group on :b:c: cannot be changed) based on groupRenameAttempted"
        if (groupRenameAttempted) {
            fails(taskToRun)
            failureHasCause("Cannot set group on project ':b:c' because it is already a dependency of project ':a'.  A project's GAV coordinates cannot change after a it becomes a dependency of another project.")
        } else {
            succeeds(taskToRun)
        }

        where:
        taskToRun                   | configureOnDemand || groupRenameAttempted || explanation
        ':a:tasks'                  | true              || false                || "calling tasks on project a, with configure on demand set, project b's is never configured and project c is never renamed"
        ':a:tasks'                  | false             || true                 || "calling tasks on project a, without configure on demand set, project b is configured and project c is renamed"
        ':a:checkCompileClasspath'  | true              || true                 || "calling checkCompileClasspath on project a forces project b to be configured regardless of configure on demand = true, and project c is renamed"
        ':a:checkCompileClasspath'  | false             || true                 || "calling checkCompileClasspath on project a forces project b to be configured regardless of configure on demand = false,  and project c is renamed"
    }
}
