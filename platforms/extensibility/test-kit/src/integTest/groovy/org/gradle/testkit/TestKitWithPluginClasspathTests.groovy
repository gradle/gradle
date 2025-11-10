/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.testkit

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.util.internal.TextUtil
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

@ToBeImplemented
@Issue("https://github.com/gradle/gradle/issues/22466")
class TestKitWithPluginClasspathTests extends AbstractIntegrationSpec {

    // A plugin that will be applied by both plugin-other and plugin-tested
    // This plugin will be published to a local maven repository
    TestFile pluginLeafDir = testDirectory.createDir("plugin-leaf")
    // A plugin that serves as "another" plugin that applies plugin-leaf
    // This plugin will be published to a local maven repository
    TestFile pluginOtherDir = testDirectory.createDir("plugin-other")
    // Our main plugin under test
    // It will, just as "plugin-other" apply "plugin-leaf", causing issues
    // It will implement a TestKit test, and use withPluginClasspath() to inject itself into the tests
    // This plugin WON'T be published to the local maven repository
    TestFile pluginTestedDir = testDirectory.createDir("plugin-tested")

    // This is the project that we will run using TestKit inside "plugin-tested"
    TestFile testKitProjectDir = testDirectory.createDir("testkit-project")

    // This directory will hold a local maven repository
    // This reproducer needs to pretend that plugin-leaf and plugin-other are external plugins
    TestFile localRepoDir = testDirectory.createDir("local-repo")

    def setup() {
        testDirectory.file("settings.gradle") << """
            rootProject.name = "22466-reproducer"

            include("plugin-leaf")
            include("plugin-other")
            include("plugin-tested")

            dependencyResolutionManagement {
                // We will need JUnit for the tests in plugin-tested
                ${mavenCentralRepository()}
            }
        """

        // PLUGIN-LEAF PROJECT
        def pluginLeafBuilder = new PluginBuilder(pluginLeafDir)
        pluginLeafBuilder.packageName = "com.example"
        pluginLeafBuilder.addPluginId('com.example.leaf-plugin', 'LeafPlugin')
        pluginLeafBuilder.applyBuildScriptPlugin('maven-publish')
        pluginLeafBuilder.java("LeafPlugin.java") << """
            package com.example;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class LeafPlugin implements Plugin<Project> {
                @Override
                public void apply(Project target) {
                    System.out.println("Applying LeafPlugin");
                }
            }
        """
        pluginLeafBuilder.addBuildScriptContent("""
            publishing {
                repositories {
                    maven {
                        name = 'localRepo'
                        url = uri('${TextUtil.escapeString(localRepoDir.absolutePath)}')
                    }
                }
            }
        """)
        pluginLeafBuilder.prepareToExecute()

        // PLUGIN-OTHER PROJECT
        def pluginOtherBuilder = new PluginBuilder(pluginOtherDir)
        pluginOtherBuilder.packageName = "com.example"
        pluginOtherBuilder.addPluginId('com.example.other-plugin', 'OtherPlugin')
        pluginOtherBuilder.applyBuildScriptPlugin('maven-publish')
        pluginOtherBuilder.java("OtherPlugin.java") << """
            package com.example;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class OtherPlugin implements Plugin<Project> {
                @Override
                public void apply(Project target) {
                    System.out.println("Applying OtherPlugin");
                    target.getPlugins().apply(LeafPlugin.class);
                }
            }
        """
        pluginOtherBuilder.addBuildScriptContent("""
            dependencies {
                implementation(project(":plugin-leaf"))
            }

            publishing {
                repositories {
                    maven {
                        name = 'localRepo'
                        url = uri('${TextUtil.escapeString(localRepoDir.absolutePath)}')
                    }
                }
            }
        """)
        pluginOtherBuilder.prepareToExecute()

        // PLUGIN-TESTED PROJECT
        def pluginTestedBuilder = new PluginBuilder(pluginTestedDir)
        pluginTestedBuilder.packageName = "com.example"
        pluginTestedBuilder.addPluginId('com.example.tested-plugin', 'RootPlugin')
        pluginTestedBuilder.java("RootPlugin.java") << """
            package com.example;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class RootPlugin implements Plugin<Project> {
                @Override
                public void apply(Project target) {
                    System.out.println("Applying RootPlugin");
                }
            }
        """
        pluginTestedBuilder.testJava("RootPluginTest.java") << """
            package com.example;

            import org.gradle.testkit.runner.BuildResult;
            import org.gradle.testkit.runner.GradleRunner;
            import org.junit.jupiter.api.BeforeEach;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.io.TempDir;

            import java.io.File;
            import java.io.Writer;
            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.util.Arrays;
            import java.util.Objects;

            import static org.junit.jupiter.api.Assertions.assertFalse;
            import static org.junit.jupiter.api.Assertions.assertTrue;

            public class RootPluginTest {

                @Test
                void test() throws Exception {
                    File projectDir = new File("${TextUtil.escapeString(testKitProjectDir.absolutePath)}");
                    Writer outputWriter = Files.newBufferedWriter(
                        Path.of(
                            "${TextUtil.escapeString(testDirectory.absolutePath)}", "output.txt"
                        )
                    );

                    BuildResult result = GradleRunner.create()
                            .withProjectDir(projectDir)
                            .withPluginClasspath()
                            .forwardStdOutput(outputWriter)
                            .build();
                }
            }
        """
        pluginTestedBuilder.addBuildScriptContent("""
            dependencies {
                implementation(project(":plugin-leaf"))
                testImplementation(gradleTestKit())
                testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
                testRuntimeOnly("org.junit.platform:junit-platform-launcher")
            }

            tasks.named('test') {
                useJUnitPlatform()
            }
        """)
        pluginTestedBuilder.prepareToExecute()

        // TEST-KIT PROJECT
        // Note: build.gradle will be filled in depending the test scenario.
        testKitProjectDir.file("settings.gradle") << """
            pluginManagement {
                repositories {
                    maven {
                        url = uri('${TextUtil.escapeString(localRepoDir.absolutePath)}')
                    }
                }
            }
        """
    }

    def "withPlugin detects plugin applied transitively when tested plugin is not used"() {
        given:
        succeeds(":plugin-leaf:publish", ":plugin-other:publish")
        testKitProjectDir.file("build.gradle") << """
            plugins {
                // com.example.other-plugin applies com.example.leaf-plugin
                id 'com.example.other-plugin' version '1.0'
            }

            project.getPluginManager().withPlugin('com.example.leaf-plugin') {
                file("leaf-plugin-detected.txt").text = "Leaf plugin was detected"
            }
        """

        when:
        succeeds(":plugin-tested:test")

        then:
        testKitProjectDir.file("leaf-plugin-detected.txt").exists()
    }

    def "withPlugin does not detect plugin applied transitively when tested plugin is used"() {
        given:
        succeeds(":plugin-leaf:publish", ":plugin-other:publish")
        testKitProjectDir.file("build.gradle") << """
            plugins {
                // com.example.other-plugin applies com.example.leaf-plugin
                id 'com.example.other-plugin' version '1.0'
                // com.example.testedPlugin depends on com.example.leaf-plugin
                id 'com.example.tested-plugin' version '1.0'
            }

            project.getPluginManager().withPlugin('com.example.leaf-plugin') {
                file("leaf-plugin-detected.txt").text = "Leaf plugin was detected"
            }
        """

        when:
        succeeds(":plugin-tested:test")

        then:
        !testKitProjectDir.file("leaf-plugin-detected.txt").exists()
    }

}
