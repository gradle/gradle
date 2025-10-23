package org.gradle.testkit

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
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
                repositories {
                    // Unfortunately we will need JUnit for the tests in plugin-tested
                    mavenCentral()
                }
            }
        """

        // PLUGIN-LEAF PROJECT

        pluginLeafDir.file("build.gradle") << """
            plugins {
                id 'java-gradle-plugin'
                id 'maven-publish'
            }

            group = 'com.example'
            version = '1.0'

            gradlePlugin {
                plugins {
                    leafPlugin {
                        id = 'com.example.leaf-plugin'
                        implementationClass = 'com.example.LeafPlugin'
                    }
                }
            }

            publishing {
                repositories {
                    maven {
                        name = 'localRepo'
                        url = uri('${localRepoDir.absolutePath}')
                    }
                }
            }
        """

        pluginLeafDir.file("src/main/java/com/example/LeafPlugin.java") << """
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

        // PLUGIN-OTHER PROJECT

        pluginOtherDir.file("build.gradle") << """
            plugins {
                id 'java-gradle-plugin'
                id 'maven-publish'
            }

            group = 'com.example'
            version = '1.0'

            gradlePlugin {
                plugins {
                    otherPlugin {
                        id = 'com.example.other-plugin'
                        implementationClass = 'com.example.OtherPlugin'
                    }
                }
            }

            dependencies {
                implementation(project(":plugin-leaf"))
            }

            publishing {
                repositories {
                    maven {
                        name = 'localRepo'
                        url = uri('${localRepoDir.absolutePath}')
                    }
                }
            }
        """

        pluginOtherDir.file("src/main/java/com/example/OtherPlugin.java") << """
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

        // PLUGIN-TESTED PROJECT

        pluginTestedDir.file("build.gradle") << """
            plugins {
                id 'java-gradle-plugin'
            }

            group = 'com.example'
            version = '1.0-SNAPSHOT'

            gradlePlugin {
                plugins {
                    testedPlugin {
                        id = 'com.example.tested-plugin'
                        implementationClass = 'com.example.RootPlugin'
                    }
                }
            }

            dependencies {
                implementation(project(":plugin-leaf"))
                testImplementation(gradleTestKit())
                testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
                testRuntimeOnly("org.junit.platform:junit-platform-launcher")
            }

            tasks.named('test') {
                useJUnitPlatform()
            }
        """

        pluginTestedDir.file("src/main/java/com/example/RootPlugin.java") << """
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

        pluginTestedDir.file("src/test/java/com/example/RootPluginTest.java") << """
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
                    File projectDir = new File("${testKitProjectDir.absolutePath}");
                    Writer outputWriter = Files.newBufferedWriter(
                        Path.of(
                            "${testDirectory.absolutePath}/output.txt"
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

        // TEST-KIT PROJECT
        // Note: build.gradle will be filled in depending the test scenario.
        testKitProjectDir.file("settings.gradle") << """
            pluginManagement {
                repositories {
                    maven {
                        url = uri('${localRepoDir.absolutePath}')
                    }
                }
                resolutionStrategy {
                    eachPlugin {
                        if (requested.id.id.startsWith('com.example.')) {
                            useVersion('1.0')
                        }
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
                id 'com.example.other-plugin'
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
                id 'com.example.other-plugin'
                // com.example.testedPlugin depends on com.example.leaf-plugin
                id 'com.example.tested-plugin'
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
