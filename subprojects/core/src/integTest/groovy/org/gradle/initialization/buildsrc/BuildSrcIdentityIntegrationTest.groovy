/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.initialization.buildsrc

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects

import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.INFO_DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.integtests.fixtures.SuggestionsMessages.STACKTRACE_MESSAGE
import static org.gradle.integtests.fixtures.SuggestionsMessages.repositoryHint

class BuildSrcIdentityIntegrationTest extends AbstractIntegrationSpec {
    def "includes build identifier in logging output with #display"() {
        file("buildSrc/build.gradle") << """
            println "configuring \$project.path"
            classes.doLast { t ->
                println "classes of \$t.path"
            }
        """
        file("buildSrc/settings.gradle") << settings << "\n"

        when:
        run()

        then:
        outputContains("> Configure project :buildSrc")
        result.groupedOutput.task(":buildSrc:classes").output.contains("classes of :classes")

        where:
        settings                     | display
        ""                           | "default root project name"
        "rootProject.name='someLib'" | "configured root project name"
    }

    @ToBeFixedForIsolatedProjects(because = "allprojects")
    def "includes build identifier in dependency report with #display"() {
        createDirs("buildSrc", "buildSrc/b1", "buildSrc/b2")
        file("buildSrc/settings.gradle") << """
            $settings
            include 'b1', 'b2'
        """

        file("buildSrc/build.gradle") << """
            allprojects { apply plugin: 'java' }
            dependencies { implementation project(':b1') }
            project(':b1') { dependencies { implementation project(':b2') } }
            classes.dependsOn tasks.dependencies
        """

        when:
        run()

        then:
        outputContains("""
runtimeClasspath - Runtime classpath of source set 'main'.
\\--- project :buildSrc:b1
     \\--- project :buildSrc:b2
""")

        where:
        settings                     | display
        ""                           | "default root project name"
        "rootProject.name='someLib'" | "configured root project name"
    }

    def "includes build identifier in error message on failure to resolve dependencies of build with #display"() {
        def m = mavenRepo.module("org.test", "test", "1.2")

        given:
        file("buildSrc/settings.gradle") << settings << "\n"
        def buildSrc = file("buildSrc/build.gradle")
        buildSrc << """
            repositories {
                maven { url '$mavenRepo.uri' }
            }

            dependencies {
                implementation "org.test:test:1.2"
            }
        """
        file("buildSrc/src/main/java/Thing.java") << "class Thing { }"

        when:
        fails()

        then:
        failure.assertHasDescription("Execution failed for task ':buildSrc:compileJava'.")
        failure.assertHasCause("Could not resolve all files for configuration ':buildSrc:compileClasspath'.")
        failure.assertHasCause("""Could not find org.test:test:1.2.
Searched in the following locations:
  - ${m.pom.file.displayUri}
Required by:
    project :buildSrc""")
        failure.assertHasResolutions(repositoryHint("Maven POM"),
            STACKTRACE_MESSAGE,
            INFO_DEBUG,
            SCAN,
            GET_HELP)


        when:
        m.publish()
        m.artifact.file.delete()

        fails()

        then:
        failure.assertHasDescription("Execution failed for task ':buildSrc:compileJava'.")
        failure.assertHasCause("Could not resolve all files for configuration ':buildSrc:compileClasspath'.")
        failure.assertHasCause("Could not find test-1.2.jar (org.test:test:1.2).")

        where:
        settings                     | display
        ""                           | "default root project name"
        "rootProject.name='someLib'" | "configured root project name"
    }

    def "includes build identifier in task failure error message with #display"() {
        file("buildSrc/settings.gradle") << settings << "\n"
        def buildSrc = file("buildSrc/build.gradle")
        buildSrc << """
            classes.doLast {
                throw new RuntimeException("broken")
            }
        """

        when:
        fails()

        then:
        failure.assertHasDescription("Execution failed for task ':buildSrc:classes'.")
        failure.assertHasCause("broken")

        where:
        settings                     | display
        ""                           | "default root project name"
        "rootProject.name='someLib'" | "configured root project name"
    }

    @ToBeFixedForIsolatedProjects(because = "Configure projects from root")
    def "includes build identifier in dependency resolution results with #display"() {
        given:
        createDirs("buildSrc", "buildSrc/a")
        file("buildSrc/settings.gradle") << """
            ${settings}
            include 'a'
        """
        file("buildSrc/build.gradle") << """
            dependencies {
                implementation project(':a')
            }
            project(':a') {
                apply plugin: 'java'
            }
            classes.doLast {
                def components = configurations.compileClasspath.incoming.resolutionResult.allComponents.id
                assert components.size() == 2
                assert components[0].build.buildPath == ':buildSrc'
                assert components[0].build.name == 'buildSrc'
                assert components[0].build.currentBuild
                assert components[0].projectPath == ':'
                assert components[0].projectName == '$rootProjectName'
                assert components[0].buildTreePath == ':buildSrc'
                assert components[1].build.buildPath == ':buildSrc'
                assert components[1].build.name == 'buildSrc'
                assert components[1].build.currentBuild
                assert components[1].projectPath == ':a'
                assert components[1].projectName == 'a'
                assert components[1].buildTreePath == ':buildSrc:a'

                def selectors = configurations.runtimeClasspath.incoming.resolutionResult.allDependencies.requested
                assert selectors.size() == 1
                assert selectors[0].displayName == 'project :buildSrc:a'
                assert selectors[0].buildPath == ':buildSrc'
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
        succeeds()

        where:
        settings                     | rootProjectName | display
        ""                           | "buildSrc"      | "default root project name"
        "rootProject.name='someLib'" | "someLib"       | "configured root project name"
    }
}
