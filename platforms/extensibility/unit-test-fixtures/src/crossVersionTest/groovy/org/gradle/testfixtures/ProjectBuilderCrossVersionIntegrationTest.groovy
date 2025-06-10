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

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepositoryDefinition

@Issue("GRADLE-3558")
@TargetVersions("7.0+")
class ProjectBuilderCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "Requires a Gradle distribution on the test-under-test classpath, but gradleApi() does not offer the full distribution")
    def "can apply plugin using ProjectBuilder in a test running with Gradle version under development"() {
        given:
        File repoDir = file('repo')
        publishHelloWorldPluginWithOldGradleVersion(repoDir)

        when:
        writeConsumingProject(repoDir)

        then:
        version(current)
            .withTasks("test")
            .run()
    }

    private void publishHelloWorldPluginWithOldGradleVersion(File repoDir) {
        TestFile helloWorldPluginDir = temporaryFolder.createDir('hello')

        helloWorldPluginDir.with {
            file('src/main/groovy/org/gradle/hello/HelloWorldPlugin.groovy') << """
                package org.gradle.hello

                import org.gradle.api.Project
                import org.gradle.api.Plugin

                class HelloWorldPlugin implements Plugin<Project> {
                    void apply(Project project) {
                        project.tasks.create('helloWorld', HelloWorld)
                    }
                }
            """

            file('src/main/groovy/org/gradle/hello/HelloWorld.groovy') << """
                package org.gradle.hello

                import org.gradle.api.DefaultTask
                import org.gradle.api.tasks.TaskAction

                class HelloWorld extends DefaultTask {
                    @TaskAction
                    void printHelloWorld() {
                        System.out.println 'Hello world!'
                    }
                }
            """

            file('build.gradle') << """
                apply plugin: 'groovy'
                apply plugin: 'maven-publish'

                group = 'org.gradle'
                version = '1.0'

                dependencies {
                    implementation gradleApi()
                }

                publishing {
                    publications {
                        mavenJava(MavenPublication) {
                            from components.java
                        }
                    }
                    repositories {
                        maven {
                            url = '${repoDir.toURI().toURL()}'
                        }
                    }
                }
            """
        }

        version(previous)
            .inDirectory(helloWorldPluginDir)
            .withTasks("publish")
            .run()
    }

    private void writeConsumingProject(File repoDir) {
        file('src/test/java/org/gradle/consumer/PluginTest.java') << """
            package org.gradle.consumer;

            import org.gradle.api.Project;
            import org.gradle.api.Plugin;
            import org.gradle.testfixtures.ProjectBuilder;
            import org.junit.Before;
            import org.junit.Test;
            import org.gradle.hello.HelloWorldPlugin;

            public class PluginTest {
                private Project project;

                @Before
                public void setup() {
                    project = ProjectBuilder.builder().build();
                }

                @Test
                public void canApplyPlugin() {
                    project.getPlugins().apply(HelloWorldPlugin.class);
                }
            }
        """

        file('build.gradle') << """
            plugins {
                id("java-gradle-plugin")
            }

            group = 'org.gradle'
            version = '1.0'

            dependencies {
                implementation(gradleApi())
                implementation("org.gradle:hello:1.0")
                testImplementation("junit:junit:4.13")
            }

            repositories {
                maven { url = '${repoDir.toURI().toURL()}' }
                ${mavenCentralRepositoryDefinition()}
            }
        """
    }
}
