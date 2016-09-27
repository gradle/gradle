/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testfixtures

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

class ProjectBuilderCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {

    private final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    public static final String CONSUMER_GRADLE_VERSION = '2.7'
    public static final String PRODUCER_GRADLE_VERSION = '3.0'

    @NotYetImplemented
    @Issue("GRADLE-3558")
    def "can use plugin built with 2.x to execute a test on top of ProjectBuilder run with Gradle 3.x"() {
        given:
        TestFile producerProjectDir = temporaryFolder.createDir('producer')
        TestFile consumerProjectDir = temporaryFolder.createDir('consumer')
        TestFile repoDir = new TestFile(testDirectory, 'repo')

        when:
        producerProjectDir.file('src/main/groovy/org/gradle/producer/HelloWorldPlugin.groovy') << """
            package org.gradle.producer

            import org.gradle.api.Project
            import org.gradle.api.Plugin

            class HelloWorldPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.create('helloWorld', HelloWorld)
                }
            }
        """

        producerProjectDir.file('src/main/groovy/org/gradle/producer/HelloWorld.groovy') << """
            package org.gradle.producer

            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction

            class HelloWorld extends DefaultTask {
                @TaskAction
                void printHelloWorld() {
                    println 'Hello world!'
                }
            }
        """

        producerProjectDir.file('build.gradle') << """
            apply plugin: 'groovy'
            apply plugin: 'maven-publish'

            group = 'org.gradle'
            version = '1.0'

            dependencies {
                compile gradleApi()
            }

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                    }
                }
                repositories {
                    maven {
                        url '${repoDir.toURI().toURL()}'
                    }
                }
            }
        """

        then:
        createGradleExecutor(CONSUMER_GRADLE_VERSION, producerProjectDir, 'publish').run()

        when:
        consumerProjectDir.file('src/main/groovy/org/gradle/consumer/AggregationPlugin.groovy') << """
            package org.gradle.consumer

            import org.gradle.api.Project
            import org.gradle.api.Plugin

            import org.gradle.producer.HelloWorldPlugin

            class AggregationPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.apply(plugin: HelloWorldPlugin)
                }
            }
        """

        consumerProjectDir.file('src/test/java/org/gradle/consumer/PluginTest.java') << """
            package org.gradle.consumer;

            import org.gradle.api.Project;
            import org.gradle.api.Plugin;
            import org.gradle.testfixtures.ProjectBuilder;
            import org.junit.Before;
            import org.junit.Test;

            public class PluginTest {
                private Project project;

                @Before
                public void setup() {
                    project = ProjectBuilder.builder().build();
                }

                @Test
                public void canApplyPlugin() {
                    project.getPlugins().apply(AggregationPlugin.class);
                }
            }
        """

        consumerProjectDir.file('build.gradle') << """
            apply plugin: 'groovy'

            group = 'org.gradle'
            version = '1.0'

            dependencies {
                compile gradleApi()
                compile 'org.gradle:producer:1.0'
                testCompile 'junit:junit:4.12'
            }

            repositories {
                maven { url '${repoDir.toURI().toURL()}' }
                mavenCentral()
            }
        """

        then:
        createGradleExecutor(PRODUCER_GRADLE_VERSION, consumerProjectDir, 'test').run()
    }

    private GradleExecuter createGradleExecutor(String gradleVersion, File projectDir, String... tasks) {
        version(buildContext.distribution(gradleVersion)).inDirectory(projectDir).withTasks(tasks)
    }
}
