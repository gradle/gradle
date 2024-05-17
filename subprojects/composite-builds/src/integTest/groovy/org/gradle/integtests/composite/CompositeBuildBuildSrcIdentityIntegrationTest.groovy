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

    def "includes build identifier in logging output with #display"() {
        dependency "org.test:buildB:1.0"

        buildB.file("buildSrc/settings.gradle") << settings << "\n"
        buildB.file("buildSrc/build.gradle") << """
            println "configuring \$project.path"
            classes.doLast { t ->
                println "classes of \$t.path"
            }
        """

        when:
        execute(buildA, ":assemble")

        then:
        outputContains("> Configure project :buildB:buildSrc")
        result.groupedOutput.task(":buildB:buildSrc:classes").output.contains("classes of :classes")

        where:
        settings                     | display
        ""                           | "default root project name"
        "rootProject.name='someLib'" | "configured root project name"
    }

    def "includes build identifier in dependency report with #display"() {
        dependency "org.test:buildB:1.0"

        createDirs("buildB", "buildB/buildSrc", "buildB/buildSrc/b1", "buildB/buildSrc/b2")
        buildB.file("buildSrc/settings.gradle") << """
            $settings
            include 'b1', 'b2'
        """
        buildB.file("buildSrc/build.gradle") << """
            allprojects { apply plugin: 'java' }
            dependencies { implementation project(':b1') }
            project(':b1') { dependencies { implementation project(':b2') } }
            classes.dependsOn tasks.dependencies
        """

        when:
        execute(buildA, ":assemble")

        then:
        outputContains("""
runtimeClasspath - Runtime classpath of source set 'main'.
\\--- project :buildB:buildSrc:b1
     \\--- project :buildB:buildSrc:b2
""")

        where:
        settings                     | display
        ""                           | "default root project name"
        "rootProject.name='someLib'" | "configured root project name"
    }

    def "includes build identifier in error message on failure to resolve dependencies of build with #display"() {
        dependency "org.test:buildB:1.0"

        buildB.file("buildSrc/settings.gradle") << settings << "\n"
        buildB.file("buildSrc/build.gradle") << """
            dependencies { implementation "test:test:1.2" }
        """

        when:
        fails(buildA, ":assemble")

        then:
        failure.assertHasCause("Could not resolve all files for configuration ':buildB:buildSrc:compileClasspath'.")
        failure.assertHasCause("""Cannot resolve external dependency test:test:1.2 because no repositories are defined.
Required by:
    project :buildB:buildSrc""")

        where:
        settings                     | display
        ""                           | "default root project name"
        "rootProject.name='someLib'" | "configured root project name"
    }

    def "includes build identifier in task failure error message with #display"() {
        dependency "org.test:buildB:1.0"

        buildB.file("buildSrc/settings.gradle") << settings << "\n"
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

        where:
        settings                     | display
        ""                           | "default root project name"
        "rootProject.name='someLib'" | "configured root project name"
    }

    def "includes build identifier in dependency resolution results with #display"() {
        dependency "org.test:buildB:1.0"

        createDirs("buildB", "buildB/buildSrc", "buildB/buildSrc/a")
        buildB.file("buildSrc/settings.gradle") << """
            ${settings}
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
                assert components[0].build.buildPath == ':buildB:buildSrc'
                assert components[0].build.name == 'buildSrc'
                assert components[0].build.currentBuild
                assert components[0].projectPath == ':'
                assert components[0].projectName == '$rootProjectName'
                assert components[0].buildTreePath == ':buildB:buildSrc'
                assert components[1].build.buildPath == ':buildB:buildSrc'
                assert components[1].build.name == 'buildSrc'
                assert components[1].build.currentBuild
                assert components[1].projectPath == ':a'
                assert components[1].projectName == 'a'
                assert components[1].buildTreePath == ':buildB:buildSrc:a'

                def selectors = configurations.runtimeClasspath.incoming.resolutionResult.allDependencies.requested
                assert selectors.size() == 1
                assert selectors[0].displayName == 'project :buildB:buildSrc:a'
                assert selectors[0].buildPath == ':buildB:buildSrc'
                assert selectors[0].buildName == 'buildSrc'
                assert selectors[0].projectPath == ':a'
            }
        """

        2.times {
            executer.expectDocumentedDeprecationWarning("The BuildIdentifier.getName() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use getBuildPath() to get a unique identifier for the build. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation")
            executer.expectDocumentedDeprecationWarning("The BuildIdentifier.isCurrentBuild() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use getBuildPath() to get a unique identifier for the build. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation")
        }
        executer.expectDocumentedDeprecationWarning("The ProjectComponentSelector.getBuildName() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use getBuildPath() to get a unique identifier for the build. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation")

        expect:
        execute(buildA, ":assemble")

        where:
        settings                     | rootProjectName | display
        ""                           | "buildSrc"      | "default root project name"
        "rootProject.name='someLib'" | "someLib"       | "configured root project name"
    }
}


