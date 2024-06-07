/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

// Because of TestKit
@Requires(IntegTestPreconditions.NotEmbeddedExecutor)
class PublicApiIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {
    // Need to pin this to a specific JVM version to avoid Kotlin complaining about using a different version to Java
    def jvm = AvailableJavaHomes.jdk17

    def apiJarRepoLocation = new File(System.getProperty('integTest.apiJarRepoLocation'))
    def apiJarVersion = System.getProperty("integTest.distZipVersion")
    def kotlinVersion = System.getProperty("integTest.kotlinVersion")

    def setup() {
        executer.beforeExecute {
            args("-Dorg.gradle.unsafe.suppress-gradle-api=true")
            withInstallations(jvm)
        }

        file("src/test/java/org/example/PublishedApiTestPluginTest.java") << pluginTestJava()
    }

    def "can compile Java code against public API"() {
        buildFile << configureApiWithPlugin('id("java-library")')

        file("src/main/java/org/example/PublishedApiTestPlugin.java") << """
            package org.example;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class PublishedApiTestPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    System.out.println("Hello from plugin");
                    project.getTasks().register("customTask", CustomTask.class);
                }
            }
        """
        file("src/main/java/org/example/CustomTask.java") << """
            package org.example;

            import javax.annotation.Nullable;
            import javax.inject.Inject;
            import org.gradle.api.DefaultTask;
            import org.gradle.api.file.FileSystemOperations;
            import org.gradle.api.tasks.TaskAction;

            public abstract class CustomTask extends DefaultTask {
                @Inject
                public abstract FileSystemOperations getFileSystemOperations();

                @TaskAction
                public void customAction() {
                    System.out.println("Hello from custom task");
                }

                @Nullable
                private String doThatThing() {
                    return null;
                }
            }
        """

        expect:
        succeeds(":test")
    }

    def "can compile Groovy code against public API"() {
        buildFile << configureApiWithPlugin('id("groovy")')

        file("src/main/groovy/org/example/PublishedApiTestPlugin.groovy") << """
            package org.example

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class PublishedApiTestPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.register("myTask", CustomTask) {
                        mapValues = ["alma": 1, "bela": 2]
                        println("Hello from plugin")
                    }
                }
            }
        """
        file("src/main/groovy/org/example/CustomTask.groovy") << """
            package org.example

            import org.gradle.api.DefaultTask
            import org.gradle.api.provider.MapProperty
            import org.gradle.api.tasks.Input
            import org.gradle.api.tasks.TaskAction

            @groovy.transform.CompileStatic
            abstract class CustomTask extends DefaultTask {
                // This is to test org.gradle.api.internal.provider.MapPropertyExtensions
                @Input
                abstract MapProperty<String, Integer> getMapValues()

                @TaskAction
                void customAction() {
                    println("Hello from custom task")
                    println("- mapValues['alma'] = \${mapValues['alma']}")
                }
            }
        """

        expect:
        succeeds(":compileGroovy")
    }

    def "can compile Kotlin code against public API"() {
        buildFile << configureApiWithPlugin("id(\"org.jetbrains.kotlin.jvm\") version \"${kotlinVersion}\"")

        file("src/main/kotlin/org/example/PublishedApiTestPlugin.kt") << """
            package org.example

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class PublishedApiTestPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    project.tasks.register("myTask", CustomTask::class.java) { task ->
                        task.mapValues.set(mapOf("alma" to 1, "bela" to 2))
                        println("Hello from plugin")
                    }
                }
            }
        """
        file("src/main/kotlin/org/example/CustomTask.kt") << """
            package org.example

            import org.gradle.api.DefaultTask
            import org.gradle.api.provider.MapProperty
            import org.gradle.api.tasks.Input
            import org.gradle.api.tasks.TaskAction

            abstract class CustomTask : DefaultTask() {
                @get:Input
                abstract val mapValues: MapProperty<String, Int>

                @TaskAction
                fun customAction() {
                    println("Hello from custom task")
                    // println("- mapValues['alma'] = \${mapValues['alma']}")
                }
            }
        """

        expect:
        succeeds(":compileKotlin")
    }

    private configureApiWithPlugin(String pluginDefinition) {
        """
            plugins {
                id("java-gradle-plugin")
                id("jvm-test-suite")
                $pluginDefinition
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jvm.javaVersionMajor})
                }
            }

            gradlePlugin {
                plugins {
                    create("plugin") {
                        id = "org.example.test"
                        implementationClass = "org.example.PublishedApiTestPlugin"
                    }
                }
            }

            dependencies {
                implementation("org.gradle.experimental:gradle-public-api:${apiJarVersion}")
            }

            testing {
                suites {
                    test {
                        useJUnitJupiter()
                    }
                }
            }

            repositories {
                maven {
                    url = uri("${apiJarRepoLocation.toURI()}")
                }
                mavenCentral()
            }
        """
    }

    private static String pluginTestJava() {
        """
            package org.example;

            import org.gradle.testkit.runner.GradleRunner;
            import org.gradle.testkit.runner.BuildResult;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.io.TempDir;

            import java.io.File;
            import java.io.IOException;
            import java.nio.file.Files;

            import static org.junit.jupiter.api.Assertions.assertTrue;

            public class PublishedApiTestPluginTest {

                @TempDir
                File testProjectDir;

                private void createBuildFile(String content) throws IOException {
                    File buildFile = new File(testProjectDir, "build.gradle");
                    Files.write(buildFile.toPath(), content.getBytes());
                }

                @Test
                public void testCustomTask() throws IOException {
                    createBuildFile("plugins { id 'org.example.test' }");

                    BuildResult result = GradleRunner.create()
                        .withProjectDir(testProjectDir)
                        .withArguments("customTask")
                        .withPluginClasspath()
                        .build();

                    assertTrue(result.getOutput().contains("Hello from plugin"));
                    assertTrue(result.getOutput().contains("Hello from custom task"));
                }
            }
        """
    }
}
