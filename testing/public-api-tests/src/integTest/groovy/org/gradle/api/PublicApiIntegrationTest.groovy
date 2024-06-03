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

class PublicApiIntegrationTest extends AbstractIntegrationSpec {
    def apiJarRepoLocation = System.getProperty('integTest.apiJarRepoLocation')
    def apiJarVersion = System.getProperty("integTest.distZipVersion")
    def kotlinVersion = System.getProperty("integTest.kotlinVersion")

    def "can compile Java code against public API"() {
        buildFile << configureApiWithPlugin('id("java-library")')

        file("src/main/java/org/example/PublishedApiTestPlugin.java") << """
            package org.example;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class PublishedApiTestPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().register("myTask", CustomTask.class);
                }
            }
        """
        file("src/main/java/org/example/CustomTask.java") << """
            package org.example;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;

            public class CustomTask extends DefaultTask {
                @TaskAction
                public void customAction() {
                    System.out.println("Hello from CustomTask");
                }
            }
        """

        expect:
        succeeds(":compileJava")
        file("build/classes/java/main/org/example/CustomTask.class").assertIsFile()
        file("build/classes/java/main/org/example/PublishedApiTestPlugin.class").assertIsFile()
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
                    println("Hello from CustomTask")
                    println("- mapValues['alma'] = \${mapValues['alma']}")
                }
            }
        """

        expect:
        succeeds(":compileGroovy")
        file("build/classes/groovy/main/org/example/CustomTask.class").assertIsFile()
        file("build/classes/groovy/main/org/example/PublishedApiTestPlugin.class").assertIsFile()
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
                    println("Hello from CustomTask")
                    // println("- mapValues['alma'] = \${mapValues['alma']}")
                }
            }
        """

        expect:
        succeeds(":compileKotlin")
        file("build/classes/kotlin/main/org/example/CustomTask.class").assertIsFile()
        file("build/classes/kotlin/main/org/example/PublishedApiTestPlugin.class").assertIsFile()
    }

    private configureApiWithPlugin(String pluginDefinition) {
        """
            plugins {
                $pluginDefinition
            }

            repositories {
                maven {
                    url = uri("$apiJarRepoLocation")
                }
                mavenCentral()
            }

            dependencies {
                implementation("org.gradle.experimental:gradle-public-api:${apiJarVersion}")
            }
        """
    }
}
