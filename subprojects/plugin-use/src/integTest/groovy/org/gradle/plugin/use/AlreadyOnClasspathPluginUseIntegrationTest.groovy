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
package org.gradle.plugin.use

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.TextUtil

class AlreadyOnClasspathPluginUseIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        withSettings("")
    }

    private void withSettings(String settings) {
        settingsFile.text = settings.stripIndent()
        settingsFile << "\nrootProject.name = 'root'\n"
    }

    def "can request buildSrc plugin"() {

        given:
        withBinaryPluginBuild("buildSrc")

        and:
        withSettings("include('a')")

        and:
        buildFile << requestPlugin("my-plugin")
        file("a/build.gradle") << requestPlugin("my-plugin")

        when:
        succeeds "help"

        then:
        outputContains(appliedPluginOutput())

        and:
        operations.hasOperation("Apply plugin my-plugin to root project 'root'")
        operations.hasOperation("Apply plugin my-plugin to project ':a'")
    }

    def "can request non-core plugin already applied to parent project"() {

        given:
        withBinaryPluginPublishedLocally()

        and:
        withSettings """

            pluginManagement {
                ${withLocalPluginRepository()}
            }

            include('a')

        """

        and:
        buildFile << requestPlugin("my-plugin", "1.0")
        file("a/build.gradle") << requestPlugin("my-plugin")

        when:
        succeeds "help"

        then:
        operations.hasOperation("Apply plugin my-plugin to root project 'root'")
        operations.hasOperation("Apply plugin my-plugin to project ':a'")
    }

    def "can request non-core plugin already applied to grand-parent project"() {

        given:
        withBinaryPluginPublishedLocally()

        and:
        withSettings """

            pluginManagement {
                ${withLocalPluginRepository()}
            }

            include("a")
            include("a:b")

        """

        and:
        buildFile << requestPlugin("my-plugin", "1.0")
        file("a/b/build.gradle") << requestPlugin("my-plugin")

        when:
        succeeds "help"

        then:
        operations.hasOperation("Apply plugin my-plugin to root project 'root'")
        !operations.hasOperation("Apply plugin my-plugin to project ':a'")
        operations.hasOperation("Apply plugin my-plugin to project ':a:b'")
    }

    def "can request non-core plugin already requested on parent project but not applied"() {

        given:
        withBinaryPluginPublishedLocally()

        and:
        withSettings """

            pluginManagement {
                ${withLocalPluginRepository()}
            }

            include("a")

        """

        and:
        buildFile << requestPlugin("my-plugin", "1.0", false)
        file("a/build.gradle") << requestPlugin("my-plugin")

        when:
        succeeds "help"

        then:
        !operations.hasOperation("Apply plugin my-plugin to root project 'root'")
        operations.hasOperation("Apply plugin my-plugin to project ':a'")
    }

    def "can request non-core plugin already on the classpath when a plugin resolution strategy sets a version"() {

        given:
        withBinaryPluginPublishedLocally()

        and:
        withSettings """

            pluginManagement {
                ${withLocalPluginRepository()}
                resolutionStrategy { eachPlugin { useVersion("1.0") } }
            }

            include("a")

        """

        and:
        buildFile << requestPlugin("my-plugin")
        file("a/build.gradle") << requestPlugin("my-plugin")

        when:
        succeeds "help"

        then:
        operations.hasOperation("Apply plugin my-plugin to root project 'root'")
        operations.hasOperation("Apply plugin my-plugin to project ':a'")
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor) // TestKit usage inside of the test requires distribution
    def "can request plugin from TestKit injected classpath"() {

        given:
        withBinaryPluginBuild(".", new TestKitSpec(
            requestPlugin("my-plugin", null, false),
            requestPlugin("my-plugin"),
            true,
            """
                Assert.assertFalse(result.output.contains("${appliedPluginOutput(":")}"))
                Assert.assertTrue(result.output.contains("${appliedPluginOutput(":a")}"))
            """.stripIndent()))

        expect:
        succeeds "test"
    }

    def "can request plugin version of plugin already requested on parent project if it's the same"() {

        given:
        withBinaryPluginPublishedLocally()

        and:
        withSettings """

            pluginManagement {
                ${withLocalPluginRepository()}
            }

            include("a")

        """

        and:
        buildFile << requestPlugin("my-plugin", "1.0")
        file("a/build.gradle") << requestPlugin("my-plugin", "1.0")

        when:
        succeeds "help"

        then:
        operations.hasOperation("Apply plugin my-plugin to root project 'root'")
        operations.hasOperation("Apply plugin my-plugin to project ':a'")
    }

    def "cannot request plugin version of plugin already requested on parent project if the version differs"() {

        given:
        withBinaryPluginPublishedLocally()

        and:
        withSettings """

            pluginManagement {
                ${withLocalPluginRepository()}
            }

            include("a")

        """

        and:
        buildFile << requestPlugin("my-plugin", "1.0")
        file("a/build.gradle") << requestPlugin("my-plugin", "1.0.1")

        when:
        fails "help"

        then:
        failureDescriptionStartsWith("Error resolving plugin [id: 'my-plugin', version: '1.0.1']")
        failureHasCause("The request for this plugin could not be satisfied because the plugin is already on the classpath with a different version (1.0).")

        and:
        operations.hasOperation("Apply plugin my-plugin to root project 'root'")
        !operations.hasOperation("Apply plugin my-plugin to project ':a'")
    }

    def "can request plugin version of plugin already requested on settings if it's the same"() {
        given:
        withBinaryPluginPublishedLocally()

        and:
        withSettings """
            pluginManagement {
                ${withLocalPluginRepository()}
                ${requestPlugin("my-plugin", "1.0")}
            }

            include("a")
        """

        and:
        buildFile << requestPlugin("my-plugin", "1.0")
        file("a/build.gradle") << requestPlugin("my-plugin", "1.0")

        when:
        succeeds "help"

        then:
        operations.hasOperation("Apply plugin my-plugin to root project 'root'")
        operations.hasOperation("Apply plugin my-plugin to project ':a'")
    }

    // This is legal because settings only defines a _default_ version, and does not actually contribute to the classpath.
    def "can request plugin version of plugin already requested on settings if the version differs"() {
        given:
        withBinaryPluginPublishedLocally()
        withBinaryPluginPublishedLocally("my-other-local-plugins", "1.0.1")

        and:
        withSettings """
            pluginManagement {
                ${withLocalPluginRepository()}
                ${requestPlugin("my-plugin", "1.0")}
            }

            include("a")
        """

        and:
        buildFile << requestPlugin("my-plugin", "1.0.1")

        when:
        succeeds "help"

        then:
        operations.hasOperation("Apply plugin my-plugin to root project 'root'")
    }

    def "cannot request plugin version of plugin already requested on parent project if the version differs (using settings)"() {
        given:
        withBinaryPluginPublishedLocally()

        and:
        withSettings """

            pluginManagement {
                ${withLocalPluginRepository()}
                ${requestPlugin("my-plugin", "1.0")}
            }

            include("a")

        """

        and:
        buildFile << requestPlugin("my-plugin")
        file("a/build.gradle") << requestPlugin("my-plugin", "1.0.1")

        when:
        fails "help"

        then:
        failureDescriptionStartsWith("Error resolving plugin [id: 'my-plugin', version: '1.0.1']")
        failureHasCause("The request for this plugin could not be satisfied because the plugin is already on the classpath with a different version (1.0).")

        and:
        operations.hasOperation("Apply plugin my-plugin to root project 'root'")
        !operations.hasOperation("Apply plugin my-plugin to project ':a'")
    }

    def "cannot request plugin version of plugin from 'buildSrc'"() {

        given:
        withBinaryPluginBuild("buildSrc")

        and:
        buildFile << requestPlugin("my-plugin", "1.0")

        when:
        fails "help"

        then:
        failureDescriptionStartsWith("Error resolving plugin [id: 'my-plugin', version: '1.0']")
        failureHasCause("The request for this plugin could not be satisfied because the plugin is already on the classpath with an unknown version, so compatibility cannot be checked.")
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor) // TestKit usage inside of the test requires distribution
    def "cannot request plugin version of plugin from TestKit injected classpath"() {

        given:
        withBinaryPluginBuild(".", new TestKitSpec(
            requestPlugin("my-plugin", null, false),
            requestPlugin("my-plugin", "1.0"),
            false,
            """
                Assert.assertTrue(result.output.contains("Error resolving plugin [id: 'my-plugin', version: '1.0']"))
                Assert.assertTrue(result.output.contains("The request for this plugin could not be satisfied because the plugin is already on the classpath with an unknown version, so compatibility cannot be checked."))
            """.stripIndent()))

        expect:
        succeeds "test"
    }

    private static String requestPlugin(String id, String version = null, boolean apply = true) {
        """
            plugins {
                id("$id")${version == null ? "" : " version \"$version\""}${apply ? "" : " apply false"}
            }
        """.stripIndent()
    }

    private static class TestKitSpec {
        final String rootProjectBuildScript
        final String childProjectBuildScript
        final boolean succeeds
        final String testKitAssertions

        private TestKitSpec(String rootProjectBuildScript, String childProjectBuildScript, boolean succeeds, String testKitAssertions) {
            this.rootProjectBuildScript = rootProjectBuildScript
            this.childProjectBuildScript = childProjectBuildScript
            this.succeeds = succeeds
            this.testKitAssertions = testKitAssertions
        }
    }

    private void withBinaryPluginBuild(String projectPath = ".", TestKitSpec testKitSpec = null, String version = "1.0") {
        file("$projectPath/src/main/groovy/my/MyPlugin.groovy") << """

            package my

            import org.gradle.api.*

            class MyPlugin implements Plugin<Project> {
                @Override
                void apply(Project project) {
                    println("Plugin my-plugin applied! (to ${'$'}{project.path})")
                }
            }

        """.stripIndent()
        def testKitDependencies = testKitSpec ? """
            testImplementation(gradleTestKit())
            testImplementation('junit:junit:4.13')
        """ : ""
        file("$projectPath/build.gradle") << """

            plugins {
                id("groovy")
                id("java-gradle-plugin")
            }

            group = "com.acme"
            version = "$version"

            gradlePlugin {
                plugins {
                    myPlugin {
                        id = "my-plugin"
                        implementationClass = "my.MyPlugin"
                    }
                }
            }

            dependencies {
                compileOnly(gradleApi())
                $testKitDependencies
            }

            ${mavenCentralRepository()}

        """.stripIndent()
        if (testKitSpec) {
            file("src/test/groovy/my/MyPluginTest.groovy") << """

                package my

                import org.junit.*
                import org.junit.rules.*

                import org.gradle.testkit.runner.*

                class MyPluginTest {

                    @Rule public TemporaryFolder tmpDir = new TemporaryFolder()

                    @Test
                    public void assertions() {

                        // given:
                        def rootDir = tmpDir.newFolder("root")
                        new File(rootDir, "settings.gradle").text = \"\"\"
                            include("a")
                            rootProject.name = "root"
                        \"\"\".stripIndent()
                        new File(rootDir, "build.gradle").text = \"\"\"
                            ${testKitSpec.rootProjectBuildScript ?: ""}
                        \"\"\".stripIndent()
                        new File(rootDir,"a").mkdirs()
                        new File(rootDir, "a/build.gradle").text = \"\"\"
                            ${testKitSpec.childProjectBuildScript ?: ""}
                        \"\"\".stripIndent()

                        //when:
                        def runner = GradleRunner.create()
                            .withGradleInstallation(new File("${TextUtil.normaliseFileSeparators(distribution.gradleHomeDir.absolutePath)}"))
                            .withTestKitDir(new File("${TextUtil.normaliseFileSeparators(executer.gradleUserHomeDir.absolutePath)}"))
                            .withPluginClasspath()
                            .withProjectDir(rootDir)
                            .withArguments("help")
                        def result = runner.${testKitSpec.succeeds ? "build" : "buildAndFail"}()

                        // then:
                        ${testKitSpec.testKitAssertions}
                    }
                }

            """.stripIndent()
        }
    }

    private static String appliedPluginOutput(String projectPath = null) {
        "Plugin my-plugin applied!${projectPath == null ? "" : " (to $projectPath)"}"
    }

    private static String localPluginRepoPath = "local-plugin-repository"

    private static String withLocalPluginRepository() {
        """
            repositories {
                maven {
                    url = uri("$localPluginRepoPath")
                }
            }
        """.stripIndent()
    }

    private void withBinaryPluginPublishedLocally(String pluginBundleName = "my-local-plugins", String version = "1.0") {
        withBinaryPluginBuild(pluginBundleName, null, version)
        file("$pluginBundleName/settings.gradle").createFile()
        file("$pluginBundleName/build.gradle") << """
            apply plugin: "maven-publish"
            publishing { repositories { maven { url = uri("../$localPluginRepoPath") } } }
        """.stripIndent()

        executer.inDirectory(file(pluginBundleName)).withTasks("publish").run()

        file("$localPluginRepoPath/com/acme/$pluginBundleName/$version/$pluginBundleName-${version}.jar").assertExists()
        file("$localPluginRepoPath/my-plugin/my-plugin.gradle.plugin/$version/my-plugin.gradle.plugin-${version}.pom").assertExists()
    }
}
