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

package org.gradle.initialization.buildsrc


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.plugin.PluginBuilder

class BuildSrcIncludedBuildIntegrationTest extends AbstractIntegrationSpec {
    def "buildSrc cannot (yet) define any included builds"() {
        file("buildSrc/settings.gradle") << """
            includeBuild "child"
        """
        file("buildSrc/child/settings.gradle").createFile()

        when:
        fails()

        then:
        failure.assertHasDescription("Cannot include build 'child' in build 'buildSrc'. This is not supported yet.")
    }

    def "buildSrc can apply plugins contributed by other included builds"() {
        file("buildSrc/build.gradle") << """
            plugins {
                id "test-plugin"
            }
        """

        writePluginTo(file("included"))

        settingsFile << """
            includeBuild("included")
        """
        when:
        succeeds("help")
        then:
        outputContains("test-plugin applied to :buildSrc")
    }

    def "buildSrc can apply plugins contributed by other included builds from CLI"() {
        file("buildSrc/build.gradle") << """
            plugins {
                id "test-plugin"
            }
        """

        writePluginTo(file("included"))
        when:
        succeeds("help", "--include-build=included")
        then:
        outputContains("test-plugin applied to :buildSrc")
    }

    def "buildSrc can apply settings plugins contributed by other included builds"() {
        file("buildSrc/settings.gradle") << """
            plugins {
                id "test-settings-plugin"
            }
        """

        def pluginBuilder = new PluginBuilder(file("included"))
        pluginBuilder.addSettingsPlugin("println 'test-settings-plugin applied to ' + settings.gradle.publicBuildPath.buildPath")
        pluginBuilder.prepareToExecute()

        settingsFile << """
            includeBuild("included")
        """
        when:
        succeeds("help")
        then:
        outputContains("test-settings-plugin applied to :buildSrc")
    }

    def "buildSrc can apply plugins contributed by nested included build"() {
        file("buildSrc/build.gradle") << """
            plugins {
                id "test-plugin"
            }
        """

        writePluginTo(file("included/nested"))

        settingsFile << """
            includeBuild("included")
        """
        file("included/settings.gradle") << """
            includeBuild("nested")
        """
        when:
        succeeds("help")
        then:
        outputContains("test-plugin applied to :buildSrc")
    }

    def "buildSrc can depend on dependencies contributed by other included builds"() {
        file("buildSrc/build.gradle") << """
            plugins {
                id "groovy-gradle-plugin"
            }

            dependencies {
                implementation("org.gradle.test:included")
            }
        """

        file("buildSrc/src/main/groovy/apply-test-plugin.gradle") << """
            plugins {
                id("test-plugin")
            }
        """

        writePluginTo(file("included"))

        buildFile << """
            plugins {
                id "apply-test-plugin"
            }
        """
        settingsFile << """
            includeBuild("included")
        """
        when:
        succeeds("help")
        then:
        outputContains("test-plugin applied to :")
    }

    def "user gets reasonable error when included build fails to compile when buildSrc needs it"() {
        file("buildSrc/build.gradle") << """
            plugins {
                id "groovy-gradle-plugin"
            }

            dependencies {
                implementation("org.gradle.test:included")
            }
        """

        file("buildSrc/src/main/groovy/apply-test-plugin.gradle") << """
            plugins {
                id("test-plugin")
            }
        """

        writePluginTo(file("included"))
        file("included/src/main/java/Broke.java") << "does not compile!"

        buildFile << """
            plugins {
                id "apply-test-plugin"
            }
        """
        settingsFile << """
            includeBuild("included")
        """
        when:
        fails("help")
        then:
        failure.assertHasDescription("Execution failed for task ':included:compileJava'.")
        failure.assertHasCause("Compilation failed; see the compiler error output for details.")
    }

    def "buildSrc can apply plugins contributed by other included builds and use them in plugins for the root build"() {
        file("buildSrc/build.gradle") << """
            plugins {
                id "test-plugin"
                id "groovy-gradle-plugin"
            }

            dependencies {
                implementation("org.gradle.test:included")
            }
        """

        file("buildSrc/src/main/groovy/apply-test-plugin.gradle") << """
            plugins {
                id("test-plugin")
            }
        """

        writePluginTo(file("included"))

        buildFile << """
            plugins {
                id "apply-test-plugin"
            }
        """
        settingsFile << """
            includeBuild("included")
        """
        when:
        succeeds("help")
        then:
        outputContains("test-plugin applied to :buildSrc")
        outputContains("test-plugin applied to :")
    }

    private void writePluginTo(TestFile projectDir) {
        def pluginBuilder = new PluginBuilder(projectDir)
        pluginBuilder.addPlugin("println 'test-plugin applied to ' + project.gradle.publicBuildPath.buildPath")
        pluginBuilder.prepareToExecute()
    }

}
