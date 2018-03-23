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

class CompositeBuildBuildSrcIdentityIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB

    def setup() {
        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            file("buildSrc/src/main/java/Thing.java") << "class Thing { }"
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

        buildB.file("buildSrc/build.gradle") << """
            dependencies { implementation "test:test:1.2" }
        """

        when:
        fails(buildA, ":assemble")

        then:
        failure.assertHasDescription("Could not resolve all files for configuration ':buildB:buildSrc:runtimeClasspath'.")
        // TODO - incorrect project path
        failure.assertHasCause("""Cannot resolve external dependency test:test:1.2 because no repositories are defined.
Required by:
    project :buildSrc""")
    }

    def "includes build identifier in task failure error message"() {
        dependency 'org.test:buildB:1.0'

        buildB.file("buildSrc/build.gradle") << """
            classes.doLast {
                throw new RuntimeException("broken")
            }
        """

        when:
        fails(buildA, ":assemble")

        then:
        failure.assertHasDescription("Execution failed for task ':buildB:buildSrc:classes'.")
        failure.assertHasCause("broken")
    }

    def "includes build identifier in dependency resolution results"() {
        dependency 'org.test:buildB:1.0'

        buildB.file("buildSrc/settings.gradle") << """
            include 'a'
        """
        buildB.file("buildSrc/build.gradle") << """
            project(':a') {
                apply plugin: 'java'
            }
            dependencies {
                implementation project(':a')
            }
            classes.doLast {
                def components = configurations.compileClasspath.incoming.resolutionResult.allComponents.id
                assert components.size() == 2
                // TODO - should encode 'buildB' somewhere
                assert components[0].build.name == 'buildSrc'
                assert components[0].build.currentBuild
                assert components[0].projectPath == ':'
                assert components[0].projectName == 'buildSrc'
                assert components[1].build.name == 'buildSrc'
                assert components[1].build.currentBuild
                assert components[1].projectPath == ':a'
                assert components[1].projectName == 'a'
            }
        """

        expect:
        execute(buildA, ":assemble")
    }
}


