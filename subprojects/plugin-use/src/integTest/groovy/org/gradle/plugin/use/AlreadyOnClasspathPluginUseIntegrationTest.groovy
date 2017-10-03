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

class AlreadyOnClasspathPluginUseIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        settingsFile << "rootProject.name = 'root'\n"
    }

    def "can request buildSrc plugin"() {

        given:
        withBinaryPluginBuild("buildSrc")

        and:
        settingsFile << "include('a')\n"

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
        settingsFile << "include('a')\n"

        and:
        buildFile << requestPlugin("com.gradle.plugin-publish", "0.9.8")
        file("a/build.gradle") << requestPlugin("com.gradle.plugin-publish")

        when:
        succeeds "help"

        then:
        operations.hasOperation("Apply plugin com.gradle.plugin-publish to root project 'root'")
        operations.hasOperation("Apply plugin com.gradle.plugin-publish to project ':a'")
    }

    def "can request non-core plugin already applied to grand-parent project"() {

        given:
        settingsFile << """

            include("a")
            include("a:b")

        """.stripIndent()

        and:
        buildFile << requestPlugin("com.gradle.plugin-publish", "0.9.8")
        file("a/b/build.gradle") << requestPlugin("com.gradle.plugin-publish")

        when:
        succeeds "help"

        then:
        operations.hasOperation("Apply plugin com.gradle.plugin-publish to root project 'root'")
        !operations.hasOperation("Apply plugin com.gradle.plugin-publish to project ':a'")
        operations.hasOperation("Apply plugin com.gradle.plugin-publish to project ':a:b'")
    }

    def "can request non-core plugin already requested on parent project but not applied"() {

        given:
        settingsFile << "include('a')\n"

        and:
        buildFile << requestPlugin("com.gradle.plugin-publish", "0.9.8", false)
        file("a/build.gradle") << requestPlugin("com.gradle.plugin-publish")

        when:
        succeeds "help"

        then:
        !operations.hasOperation("Apply plugin com.gradle.plugin-publish to root project 'root'")
        operations.hasOperation("Apply plugin com.gradle.plugin-publish to project ':a'")
    }

    def "can request non-core plugin with same version as plugin already on classpath"() {

        given:
        settingsFile << "include('a')\n"

        and:
        buildFile << requestPlugin("com.gradle.plugin-publish", "0.9.8")
        file("a/build.gradle") << requestPlugin("com.gradle.plugin-publish", "0.9.8")

        when:
        succeeds "help"

        then:
        operations.hasOperation("Apply plugin com.gradle.plugin-publish to root project 'root'")
        operations.hasOperation("Apply plugin com.gradle.plugin-publish to project ':a'")
    }

    def "can request non-core plugin already on the classpath when a plugin resolution strategy sets a version"() {

        given:
        settingsFile.text = """

            pluginManagement {
                resolutionStrategy { eachPlugin { useVersion("0.9.8") } }
            }

            ${settingsFile.text}

            include("a")

        """.stripIndent()

        and:
        buildFile << requestPlugin("com.gradle.plugin-publish")
        file("a/build.gradle") << requestPlugin("com.gradle.plugin-publish")

        when:
        succeeds "help"

        then:
        operations.hasOperation("Apply plugin com.gradle.plugin-publish to root project 'root'")
        operations.hasOperation("Apply plugin com.gradle.plugin-publish to project ':a'")
    }

    def "can request plugin from TestKit injected classpath"() {

        given:
        withBinaryPluginBuild(".", new TestKitSpec(
            requestPlugin("my-plugin"),
            requestPlugin("my-plugin"),
            true,
            """
                Assert.assertTrue(result.output.contains("${appliedPluginOutput(":")}"))
                Assert.assertTrue(result.output.contains("${appliedPluginOutput(":a")}"))
            """.stripIndent()))

        expect:
        succeeds "test"
    }

    def "cannot request plugin with different version than plugin already on classpath"() {

        given:
        settingsFile << "include('a')\n"

        and:
        buildFile << requestPlugin("com.gradle.plugin-publish", "0.9.8")
        file("a/build.gradle") << requestPlugin("com.gradle.plugin-publish", "0.9.7")

        when:
        fails "help"

        then:
        failureHasCause("Cannot apply version 0.9.7 of 'com.gradle.plugin-publish' as version 0.9.8 is already on the classpath")

        and:
        operations.hasOperation("Apply plugin com.gradle.plugin-publish to root project 'root'")
        !operations.hasOperation("Apply plugin com.gradle.plugin-publish to project ':a'")
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
        failureHasCause("Plugins with unknown version (e.g. from 'buildSrc' or TestKit injected classpath) cannot be requested with a version")
    }

    def "cannot request plugin version of plugin from TestKit injected classpath"() {

        given:
        withBinaryPluginBuild(".", new TestKitSpec(
            requestPlugin("my-plugin"),
            requestPlugin("my-plugin", "1.0"),
            false,
            """
                Assert.assertTrue(result.output.contains("Error resolving plugin [id: 'my-plugin', version: '1.0']"))
                Assert.assertTrue(result.output.contains("Plugins with unknown version (e.g. from 'buildSrc' or TestKit injected classpath) cannot be requested with a version"))
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

    private void withBinaryPluginBuild(String projectPath = ".", TestKitSpec testKitSpec = null) {
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
            testImplementation('junit:junit:4.12')
        """ : ""
        file("$projectPath/build.gradle") << """

            plugins {
                id("groovy")
                id("java-gradle-plugin")
            }

            group = "com.acme"
            version = "1.0"
            
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

            ${jcenterRepository()}

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
                            ${testKitSpec.rootProjectBuildScript}
                        \"\"\".stripIndent()
                        new File(rootDir,"a").mkdirs()
                        new File(rootDir, "a/build.gradle").text = \"\"\"
                            ${testKitSpec.childProjectBuildScript}
                        \"\"\".stripIndent()
    
                        //when:
                        def runner = GradleRunner.create()
                            .withGradleInstallation(new File("${distribution.gradleHomeDir.absolutePath.replace("\\", "\\\\")}"))
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
}
