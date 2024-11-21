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
import org.gradle.test.fixtures.Flaky
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.plugin.PluginBuilder

@Flaky(because = "https://github.com/gradle/gradle-private/issues/4550")
class BuildSrcIncludedBuildIntegrationTest extends AbstractIntegrationSpec {
    def "buildSrc can use a library contributed by a build that it includes"() {
        file("buildSrc/settings.gradle") << """
            includeBuild("../included")
        """
        file("buildSrc/build.gradle") << """
            dependencies {
                implementation("test.lib:lib:1.0")
            }
        """
        writeLibraryTo(file("included"))
        useLibraryFrom(file("buildSrc"))

        when:
        run()

        then:
        result.assertTaskExecuted(":included:jar")
        result.assertTaskExecuted(":buildSrc:jar")
    }

    def "buildSrc can use a library contributed by a build included by the root build"() {
        file("buildSrc/build.gradle") << """
            dependencies {
                implementation("test.lib:lib:1.0")
            }
        """
        writeLibraryTo(file("included"))
        useLibraryFrom(file("buildSrc"))

        settingsFile << """
            includeBuild("included")
        """

        when:
        run()

        then:
        result.assertTaskExecuted(":included:jar")
        result.assertTaskExecuted(":buildSrc:jar")
    }

    // buildSrc acts like an implicit pluginManagement { } included build
    def "library contributed by buildSrc is not visible to the root build"() {
        writeLibraryTo(file("buildSrc"))

        file("build.gradle") << """
            plugins { id("java-library") }
            dependencies {
                implementation("test.lib:lib:1.0")
            }
        """
        useLibraryFrom(testDirectory)

        when:
        fails("build")

        then:
        failure.assertTaskExecuted(":buildSrc:jar")
        failure.assertHasCause("Cannot resolve external dependency test.lib:lib:1.0 because no repositories are defined.")
    }

    // buildSrc acts like an implicit pluginManagement { } included build
    def "library contributed by buildSrc is not visible to a build included by the root build"() {
        writeLibraryTo(file("buildSrc"))

        settingsFile << """
            includeBuild("included")
        """
        file("included/build.gradle") << """
            plugins { id("java-library") }
            dependencies {
                implementation("test.lib:lib:1.0")
            }
        """
        useLibraryFrom(file("included"))

        when:
        fails(":included:build")

        then:
        failure.assertTaskExecuted(":buildSrc:jar")
        failure.assertHasCause("Cannot resolve external dependency test.lib:lib:1.0 because no repositories are defined.")
    }

    def "buildSrc can apply plugins contributed by a build that it includes"() {
        file("buildSrc/settings.gradle") << """
            includeBuild("../included")
        """
        file("buildSrc/build.gradle") << """
            plugins {
                id "test-plugin"
            }
        """
        writePluginTo(file("included"))

        when:
        succeeds("help")
        then:
        outputContains("test-plugin applied to :buildSrc")
    }

    def "buildSrc can apply plugins contributed by a build included by the root build"() {
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

    def "plugin contributes by buildSrc are not visible to a build included by the root build"() {
        writePluginTo(file("buildSrc"))

        settingsFile << """
            includeBuild("included")
        """
        file("included/build.gradle") << """
            plugins {
                id "test-plugin"
            }

        """
        when:
        fails("help")
        then:
        failure.assertHasDescription("Plugin [id: 'test-plugin'] was not found in any of the following sources")
    }

    def "buildSrc can apply plugins contributed by a build included from CLI"() {
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

    def "buildSrc can apply plugins contributed by a build that it includes via pluginManagement"() {
        file("buildSrc/settings.gradle") << """
            pluginManagement {
                includeBuild("../included")
            }
        """
        file("buildSrc/build.gradle") << """
            plugins {
                id "test-plugin"
            }
        """
        writePluginTo(file("included"))

        when:
        succeeds("help")
        then:
        outputContains("test-plugin applied to :buildSrc")
    }

    def "buildSrc can apply settings plugins contributed by a build it includes via pluginManagement"() {
        file("buildSrc/settings.gradle") << """
            pluginManagement {
                includeBuild("../included")
            }
            plugins {
                id "test-settings-plugin"
            }
        """

        def pluginBuilder = new PluginBuilder(file("included"))
        pluginBuilder.addSettingsPlugin("println 'test-settings-plugin applied to ' + settings.gradle.publicBuildPath.buildPath")
        pluginBuilder.prepareToExecute()

        when:
        succeeds("help")
        then:
        outputContains("test-settings-plugin applied to :buildSrc")
    }

    def "buildSrc can apply settings plugins contributed by a build included by the root build"() {
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

    def "buildSrc can depend on plugins contributed by a build included by the root build"() {
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
        failure.assertHasCause("Compilation failed; see the compiler output below.")
    }

    def "buildSrc can apply plugins contributed by a build included by the root build and use them in plugins for the root build"() {
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

    private void writeLibraryTo(TestFile projectDir) {
        projectDir.file("settings.gradle") << """
            rootProject.name = "lib"
        """
        projectDir.file("build.gradle") << """
            plugins { id("java-library") }

            group = "test.lib"
        """
        projectDir.file("src/main/java/lib/Lib.java") << """
            package lib;
            public class Lib { }
        """
    }

    private void useLibraryFrom(TestFile projectDir) {
        projectDir.file("src/main/java/Consumer.java") << """
            import lib.Lib;
            class Consumer {
                Lib lib = new Lib();
            }
        """
    }
}
