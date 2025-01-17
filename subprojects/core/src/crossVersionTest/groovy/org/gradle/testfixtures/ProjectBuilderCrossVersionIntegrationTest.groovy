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

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepositoryDefinition

@Issue("GRADLE-3558")
@Requires(UnitTestPreconditions.Jdk11OrEarlier)
// Avoid testing version range in favor of better coverage build performance.
@TargetVersions(['5.0', '6.8'])
class ProjectBuilderCrossVersionIntegrationTest extends MultiVersionIntegrationSpec {

    public static final String TEST_TASK_NAME = 'test'

    private final List<GradleExecuter> executers = []

    def cleanup() {
        executers.each { it.cleanup() }
    }

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "Requires a Gradle distribution on the test-under-test classpath, but gradleApi() does not offer the full distribution")
    def "can apply plugin using ProjectBuilder in a test running with Gradle version under development"() {
        writeSourceFiles()
        expect:
        run TEST_TASK_NAME
    }

    private void writeSourceFiles() {
        File repoDir = file('repo')
        publishHelloWorldPluginWithOldGradleVersion(repoDir)
        writeConsumingProject(repoDir)
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
                            url = '${repoDir.toURI().toURL()}'
                        }
                    }
                }
            """
        }

        createGradleExecutor(version, helloWorldPluginDir, 'publish').noDeprecationChecks().run()
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
            apply plugin: 'groovy'

            group = 'org.gradle'
            version = '1.0'

            dependencies {
                'implementation' gradleApi()
                'implementation' 'org.gradle:hello:1.0'
                'testImplementation' 'junit:junit:4.13'
            }

            repositories {
                maven { url = '${repoDir.toURI().toURL()}' }
                ${mavenCentralRepositoryDefinition()}
            }
        """
    }

    private GradleExecuter createGradleExecutor(String gradleVersion, File projectDir = testDirectory, String... tasks) {
        def executer = buildContext.distribution(gradleVersion).executer(temporaryFolder, getBuildContext())
        executer.inDirectory(projectDir)
        executer.withTasks(tasks)
        executers << executer
        executer
    }
}
