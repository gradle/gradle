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

    def "includes build identifier in logging output with #display"() {
        dependency "org.test:${dependencyName}:1.0"

        buildB.settingsFile << settings << "\n"
        buildB.buildFile << """
            println "configuring \$project.path"
            classes.doLast { t ->
                println "classes of \$t.path"
            }
        """

        when:
        execute(buildA, ":assemble")

        then:
        outputContains("> Configure project :${buildName}")
        result.groupedOutput.task(":${buildName}:classes").output.contains("classes of :classes")

        where:
        settings                     | buildName | dependencyName | display
        ""                           | "buildB"  | "buildB"       | "default root project name"
        "rootProject.name='someLib'" | "buildB"  | "someLib"      | "configured root project name"
    }

    def "includes build identifier in dependency report with #display"() {
        dependency "org.test:${dependencyName}:1.0"

        buildB.settingsFile << settings << "\n"
        buildB.buildFile << """
            dependencies { implementation project(':b1') }
        """

        when:
        execute(buildA, ":dependencies")

        then:
        outputContains("""
runtimeClasspath - Runtime classpath of source set 'main'.
\\--- org.test:${dependencyName}:1.0 -> project :${buildName}
     \\--- project :${buildName}:b1
""")

        where:
        settings                     | buildName | dependencyName | display
        ""                           | "buildB"  | "buildB"       | "default root project name"
        "rootProject.name='someLib'" | "buildB"  | "someLib"      | "configured root project name"
    }

    def "includes build identifier in error message on failure to resolve dependencies of build with #display"() {
        dependency "org.test:${dependencyName}:1.0"

        buildB.settingsFile << settings << "\n"
        buildB.buildFile << """
            dependencies { implementation "test:test:1.2" }
        """

        when:
        fails(buildA, ":assemble")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':${buildName}:compileJava'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':${buildName}:compileClasspath'.")
        failure.assertHasCause("""Cannot resolve external dependency test:test:1.2 because no repositories are defined.
Required by:
    project :${buildName}""")

        where:
        settings                     | buildName | dependencyName | display
        ""                           | "buildB"  | "buildB"       | "default root project name"
        "rootProject.name='someLib'" | "buildB"  | "someLib"      | "configured root project name"
    }

    def "includes build identifier in task failure error message with #display"() {
        dependency "org.test:${dependencyName}:1.0"

        buildB.settingsFile << settings << "\n"
        buildB.buildFile << """
            classes.doLast {
                throw new RuntimeException("broken")
            }
        """

        when:
        fails(buildA, ":assemble")

        then:
        failure.assertHasDescription("Execution failed for task ':${buildName}:classes'.")
        failure.assertHasCause("broken")

        where:
        settings                     | buildName | dependencyName | display
        ""                           | "buildB"  | "buildB"       | "default root project name"
        "rootProject.name='someLib'" | "buildB"  | "someLib"      | "configured root project name"
    }

    def "includes build identifier in dependency resolution results with #display"() {
        dependency "org.test:${dependencyName}:1.0"

        buildB.settingsFile << settings << "\n"
        buildB.buildFile << """
            dependencies { implementation project(':b1') }
        """

        buildA.buildFile << """
            def runtimeClasspath = configurations.runtimeClasspath
            def rootProvider = runtimeClasspath.incoming.resolutionResult.rootComponent
            classes.doLast {
                def rootComponent = rootProvider.get()
                assert rootComponent.id.build.buildPath == ':'
                assert rootComponent.id.build.name == ':'
                assert rootComponent.id.build.currentBuild
                assert rootComponent.id.projectPath == ':'
                assert rootComponent.id.projectName == 'buildA'
                assert rootComponent.id.buildTreePath == ':'

                def components = rootComponent.dependencies.selected
                assert components.size() == 1
                def buildRootProject = components[0]
                def componentId = components[0].id
                assert componentId.build.buildPath == ':${buildName}'
                assert componentId.build.name == '${buildName}'
                assert !componentId.build.currentBuild
                assert componentId.projectPath == ':'
                assert componentId.projectName == '${dependencyName}'
                assert componentId.buildTreePath == ':buildB'

                components = buildRootProject.dependencies.selected
                assert components.size() == 1
                componentId = components[0].id
                assert componentId.build.buildPath == ':${buildName}'
                assert componentId.build.name == '${buildName}'
                assert !componentId.build.currentBuild
                assert componentId.projectPath == ':b1'
                assert componentId.projectName == 'b1'
                assert componentId.buildTreePath == ':buildB:b1'

                def selectors = rootComponent.dependencies.requested
                assert selectors.size() == 1
                assert selectors[0].displayName == 'org.test:${dependencyName}:1.0'

                selectors = buildRootProject.dependencies.requested
                assert selectors.size() == 1
                assert selectors[0].displayName == 'project :${buildName}:b1'
                assert selectors[0].buildPath == ':${buildName}'
                assert selectors[0].buildName == '${buildName}'
                assert selectors[0].projectPath == ':b1'
            }
        """

        3.times {
            executer.expectDocumentedDeprecationWarning("The BuildIdentifier.getName() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use getBuildPath() to get a unique identifier for the build. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation")
            executer.expectDocumentedDeprecationWarning("The BuildIdentifier.isCurrentBuild() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use getBuildPath() to get a unique identifier for the build. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation")
        }
        executer.expectDocumentedDeprecationWarning("The ProjectComponentSelector.getBuildName() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use getBuildPath() to get a unique identifier for the build. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation")

        expect:
        execute(buildA, ":assemble")

        where:
        settings                     | buildName | dependencyName | display
        ""                           | "buildB"  | "buildB"       | "default root project name"
        "rootProject.name='someLib'" | "buildB"  | "someLib"      | "configured root project name"
    }

    def "project component identifiers know if projects belong to the current build or not"() {
        def buildC = singleProjectBuild('buildC') {
            buildFile << "apply plugin: 'java'"
        }
        includeBuild(buildC)

        buildA.buildFile << """
            dependencies {
                testImplementation 'org.test:buildC'
                testImplementation 'org.test:buildA' // self dependency
            }
        """
        buildB.buildFile << """
            dependencies {
                testImplementation 'org.test:buildC'
                testImplementation 'org.test:buildB' // self dependency
            }
        """
        buildC.buildFile << """
            dependencies {
                testImplementation 'org.test:buildC' // self dependency
            }
        """

        buildA.buildFile << """
            def rootProvider = configurations.testRuntimeClasspath.incoming.resolutionResult.rootComponent
            classes.doLast {
                def rootComponent = rootProvider.get()
                def projectInOtherBuild = rootComponent.dependencies[0].selected
                def self = rootComponent.dependencies[1].selected
                assert rootComponent.id.build.currentBuild
                assert self.id.build.currentBuild
                assert self == rootComponent
                assert !projectInOtherBuild.id.build.currentBuild
            }
        """
        buildB.buildFile << """
            def rootProvider = configurations.testRuntimeClasspath.incoming.resolutionResult.rootComponent
            classes.doLast {
                def rootComponent = rootProvider.get()
                def projectInOtherBuild = rootComponent.dependencies[0].selected
                def self = rootComponent.dependencies[1].selected
                assert rootComponent.id.build.currentBuild
                assert self.id.build.currentBuild
                assert self == rootComponent
                assert !projectInOtherBuild.id.build.currentBuild
            }
        """
        buildC.buildFile << """
            def rootProvider = configurations.testRuntimeClasspath.incoming.resolutionResult.rootComponent
            classes.doLast {
                def rootComponent = rootProvider.get()
                def self = rootComponent.dependencies[0].selected
                assert rootComponent.id.build.currentBuild
                assert self.id.build.currentBuild
                assert self == rootComponent
            }
        """

        8.times { executer.expectDocumentedDeprecationWarning("The BuildIdentifier.isCurrentBuild() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use getBuildPath() to get a unique identifier for the build. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation") }

        expect:
        execute(buildA, ":buildC:assemble", ":buildB:assemble", ":assemble")
    }
}
