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
import spock.lang.Issue

/**
 * by Szczepan Faber, created at: 11/21/12
 */
class ProjectDependenciesIntegrationTest extends AbstractDependencyResolutionTest {

    @Issue("GRADLE-2477") //this is a feature on its own but also covers one of the reported issues
    def "resolving project dependency triggers configuration of the target project"() {
        settingsFile << "include 'impl'"
        buildFile << """
            apply plugin: 'java'
            dependencies {
                compile project(":impl")
            }
            repositories {
                //resolving project must declare the repo
                maven { url '${mavenRepo.uri}' }
            }
            println "Resolved at configuration time: " + configurations.compile.files*.name
        """

        mavenRepo.module("org", "foo").publish()
        file("impl/build.gradle") << """
            apply plugin: 'java'
            dependencies {
                compile "org:foo:1.0"
            }
        """

        when:
        run()

        then:
        result.output.contains "Resolved at configuration time: [impl.jar, foo-1.0.jar]"
    }

    def "configuring project dependencies by map is validated"() {
        settingsFile << "include 'impl'"
        buildFile << """
            allprojects { configurations.add('conf') }
            task extraKey << {
                def dep = dependencies.project(path: ":impl", configuration: ":conf", foo: "bar")
                assert dep.foo == "bar"
            }
            task missingPath << {
                dependencies.project(paths: ":impl", configuration: ":conf")
            }
            task missingConfiguration << {
                dependencies.project(path: ":impl", configurations: ":conf")
            }
        """

        when:
        executer.withDeprecationChecksDisabled()
        run("extraKey")

        then:
        noExceptionThrown()

        when:
        executer.withDeprecationChecksDisabled()
        run("missingConfiguration")

        then:
        noExceptionThrown()

        when:
        runAndFail("missingPath")

        then:
        failureHasCause("Required keys [path] are missing from map")
    }
}
