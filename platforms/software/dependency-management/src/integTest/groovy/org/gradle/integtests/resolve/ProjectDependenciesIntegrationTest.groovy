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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import spock.lang.Issue

@FluidDependenciesResolveTest
class ProjectDependenciesIntegrationTest extends AbstractDependencyResolutionTest {

    @Issue("GRADLE-2477") //this is a feature on its own but also covers one of the reported issues
    def "resolving project dependency triggers configuration of the target project"() {
        createDirs("impl")
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

    @ToBeFixedForConfigurationCache(because = "task uses dependencies API")
    def "configuring project dependencies by map is validated"() {
        createDirs("impl")
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
}
