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

    def "includes build identifier in logging output"() {
        dependency 'org.test:buildB:1.0'

        buildB.buildFile << """
            println "configuring \$project.path"
            classes.doLast { t ->
                println "classes of \$t.path"
            }
        """

        when:
        execute(buildA, ":assemble")

        then:
        outputContains("> Configure project :buildB")
        result.groupedOutput.task(":buildB:classes").output.contains("classes of :classes")
    }

    def "includes configured root project name in build identifier in logging output"() {
        dependency 'org.test:someLib:1.0'

        buildB.settingsFile << """
            rootProject.name = 'someLib'
        """

        buildB.buildFile << """
            println "configuring \$project.path"
            classes.doLast { t ->
                println "classes of \$t.path"
            }
        """

        when:
        execute(buildA, ":assemble")

        then:
        outputContains("> Configure project :someLib")
        result.groupedOutput.task(":someLib:classes").output.contains("classes of :classes")
    }

    def "includes build identifier in dependency report"() {
        dependency 'org.test:buildB:1.0'

        buildB.buildFile << """
            dependencies { implementation project(':b1') }
        """

        when:
        execute(buildA, ":dependencies")

        then:
        outputContains("""
runtimeClasspath - Runtime classpath of source set 'main'.
\\--- org.test:buildB:1.0 -> project :buildB
     \\--- project :buildB:b1
""")
    }

    def "includes configured root project name in build identifier in dependency report"() {
        dependency 'org.test:someLib:1.0'

        buildB.settingsFile << """
            rootProject.name = 'someLib'
        """

        buildB.buildFile << """
            dependencies { implementation project(':b1') }
        """

        when:
        execute(buildA, ":dependencies")

        then:
        outputContains("""
runtimeClasspath - Runtime classpath of source set 'main'.
\\--- org.test:someLib:1.0 -> project :someLib
     \\--- project :someLib:b1
""")
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

    def "includes configured root project name in build identifier in error message on failure to resolve dependencies of build"() {
        dependency 'org.test:someLib:1.0'

        buildB.settingsFile << """
            rootProject.name = 'someLib'
        """
        buildB.buildFile << """
            dependencies { implementation "test:test:1.2" }
        """

        when:
        fails(buildA, ":assemble")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':someLib:compileJava'.")
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':someLib:compileClasspath'.")
        failure.assertHasCause("""Cannot resolve external dependency test:test:1.2 because no repositories are defined.
Required by:
    project :someLib""")
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

    def "includes configured root project name in build identifier in task failure error message"() {
        dependency 'org.test:someLib:1.0'

        buildB.settingsFile << """
            rootProject.name = 'someLib'
        """
        buildB.buildFile << """
            classes.doLast {
                throw new RuntimeException("broken")
            }
        """

        when:
        fails(buildA, ":assemble")

        then:
        failure.assertHasDescription("Execution failed for task ':someLib:classes'.")
        failure.assertHasCause("broken")
    }

    def "includes build identifier in dependency resolution results"() {
        dependency 'org.test:buildB:1.0'
        buildB.buildFile << """
            dependencies { implementation project(':b1') }
        """

        buildA.buildFile << """
            classes.doLast {
                def components = configurations.runtimeClasspath.incoming.resolutionResult.allComponents.id
                assert components.size() == 3
                assert components[0].build.name == ':'
                assert components[0].build.currentBuild
                assert components[0].projectPath == ':'
                assert components[0].projectName == 'buildA'
                assert components[1].build.name == 'buildB'
                assert !components[1].build.currentBuild
                assert components[1].projectPath == ':'
                assert components[1].projectName == 'buildB'
                assert components[2].build.name == 'buildB'
                assert !components[2].build.currentBuild
                assert components[2].projectPath == ':b1'
                assert components[2].projectName == 'b1'

                def selectors = configurations.runtimeClasspath.incoming.resolutionResult.allDependencies.requested
                assert selectors.size() == 2
                assert selectors[0].displayName == 'org.test:buildB:1.0'
                assert selectors[1].displayName == 'project :buildB:b1'
                assert selectors[1].buildName == 'buildB'
                assert selectors[1].projectPath == ':b1'
            }
        """

        expect:
        execute(buildA, ":assemble")
    }

    def "includes configured root project name in build identifier in dependency resolution results"() {
        dependency 'org.test:someLib:1.0'
        buildB.buildFile << """
            dependencies { implementation project(':b1') }
        """

        buildA.buildFile << """
            classes.doLast {
                def components = configurations.runtimeClasspath.incoming.resolutionResult.allComponents.id
                assert components.size() == 3
                assert components[0].build.name == ':'
                assert components[0].build.currentBuild
                assert components[0].projectPath == ':'
                assert components[0].projectName == 'buildA'
                assert components[1].build.name == 'someLib'
                assert !components[1].build.currentBuild
                assert components[1].projectPath == ':'
                assert components[1].projectName == 'someLib'
                assert components[2].build.name == 'someLib'
                assert !components[2].build.currentBuild
                assert components[2].projectPath == ':b1'
                assert components[2].projectName == 'b1'

                def selectors = configurations.runtimeClasspath.incoming.resolutionResult.allDependencies.requested
                assert selectors.size() == 2
                assert selectors[0].displayName == 'org.test:someLib:1.0'
                assert selectors[1].displayName == 'project :someLib:b1'
                // TODO - should be 'someLib'
                assert selectors[1].buildName == 'buildB'
                assert selectors[1].projectPath == ':b1'
            }
        """
        buildB.settingsFile << """
            rootProject.name = 'someLib'
        """

        expect:
        execute(buildA, ":assemble")
    }
}


