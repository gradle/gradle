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
import org.gradle.util.Requires
import spock.lang.Issue

import static org.gradle.util.TestPrecondition.JDK8_OR_EARLIER

@Issue("GRADLE-3558")
@TargetVersions(['2.0', '2.7']) // Pick first incompatible version and oldest version of Gradle 2.x. Avoid testing version range in favor of better coverage build performance.
@Requires(JDK8_OR_EARLIER) // Versions < 2.10 fail to compile the plugin with Java 9 (Could not determine java version from '9-ea')
class ProjectBuilderCrossVersionIntegrationTest extends MultiVersionIntegrationSpec {

    public static final List<String> BROKEN_GRADLE_VERSIONS = ['3.0', '3.1']
    public static final String TEST_TASK_NAME = 'test'

    private final List<GradleExecuter> executers = []

    def setup() {
        writeSourceFiles()
    }

    def cleanup() {
        executers.each { it.cleanup() }
    }

    def "can apply plugin using ProjectBuilder in a test running with Gradle version under development"() {
        expect:
        run(TEST_TASK_NAME)
    }

    def "cannot apply plugin using ProjectBuilder in a test running with broken Gradle versions"() {
        expect:
        BROKEN_GRADLE_VERSIONS.each {
            def executionFailure = createGradleExecutor(it, TEST_TASK_NAME).runWithFailure()
            executionFailure.assertTestsFailed()
            executionFailure.assertOutputContains('Caused by: java.lang.ClassNotFoundException at PluginTest.java:21')
        }
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
                        println 'Hello world!'
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
                            url '${repoDir.toURI().toURL()}'
                        }
                    }
                }
            """
        }

        createGradleExecutor(version, helloWorldPluginDir, 'publish').run()
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
                compile gradleApi()
                compile 'org.gradle:hello:1.0'
                testCompile 'junit:junit:4.12'
            }

            repositories {
                maven { url '${repoDir.toURI().toURL()}' }
                mavenCentral()
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
