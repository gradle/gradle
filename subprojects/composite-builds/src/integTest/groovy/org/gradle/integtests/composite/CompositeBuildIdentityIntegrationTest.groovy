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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.build.BuildTestFile

class CompositeBuildIdentityIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB

    def setup() {
        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
"""
        }
        includedBuilds << buildB
    }

    def "includes build identifier in error message on failure to resolve dependencies of build"() {
        dependency 'org.test:buildB:1.0'

        buildB.buildFile << """
            dependencies { implementation "test:test:1.2" }
        """

        when:
        fails(buildA, ":assemble")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':buildB:compileJava'.")
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':buildB:compileClasspath'.")
        failure.assertHasCause("""Cannot resolve external dependency test:test:1.2 because no repositories are defined.
Required by:
    project :buildB""")
    }

    def "includes build identifier in task failure error message"() {
        dependency 'org.test:buildB:1.0'

        buildB.buildFile << """
            classes.doLast {
                throw new RuntimeException("broken")
            }
        """

        when:
        fails(buildA, ":assemble")

        then:
        failure.assertHasDescription("Execution failed for task ':buildB:classes'.")
        failure.assertHasCause("broken")
    }

    def "includes build identifier in dependency resolution results"() {
        dependency 'org.test:buildB:1.0'

        buildA.buildFile << """
            classes.doLast {
                def components = configurations.compileClasspath.incoming.resolutionResult.allComponents.id
                assert components.size() == 2
                assert components[0].build.name == ':'
                assert components[0].build.currentBuild
                assert components[0].projectPath == ':'
                // TODO - should be 'buildA'
                assert components[0].projectName == ':'
                assert components[1].build.name == 'buildB'
                assert !components[1].build.currentBuild
                assert components[1].projectPath == ':'
                assert components[1].projectName == 'buildB'
            }
        """

        expect:
        execute(buildA, ":assemble")
    }
}


