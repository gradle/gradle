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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

@Issue("GRADLE-3558")
class ProjectBuilderCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {

    private final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    public static final String HELLO_WORLD_PLUGIN_GRADLE_VERSION       = '2.7'
    public static final String PROJECT_BUILDER_GRADLE_VERSION          = '3.2+' /* 3.0 and 3.1 will fail as they are affected by the issue tested */
    public static final String PROJECT_BUILDER_GRADLE_VERSION_AFFECTED = '3.0'

    def "can use plugin built with 2.x to execute a test on top of ProjectBuilder run with Gradle 3.2+ in a test"() {
        given:
        def repoDir = publishHelloWorldPluginWithOldGradleVersion()
        TestFile applyPluginWithProjectBuilderDir = temporaryFolder.createDir('apply-plugin-with-project-builder')

        when:
        applyPluginWithProjectBuilderDir.file('src/test/java/org/gradle/consumer/PluginTest.java') << """
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

        applyPluginWithProjectBuilderDir.file('build.gradle') << """
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

        then:
        createGradleExecutor(PROJECT_BUILDER_GRADLE_VERSION, applyPluginWithProjectBuilderDir, 'test').run()
    }

    def "can use plugin built with 2.x to execute a test on top of ProjectBuilder run with Gradle 3.2+ in a Java application"() {
        given:
        def repoDir = publishHelloWorldPluginWithOldGradleVersion()
        TestFile applyPluginWithProjectBuilderDir = temporaryFolder.createDir('apply-plugin-with-project-builder')

        when:
        applyPluginWithProjectBuilderDir.file('src/main/java/org/gradle/consumer/App.java') << """
            package org.gradle.consumer;

            import org.gradle.api.Project;
            import org.gradle.testfixtures.ProjectBuilder;
            import org.gradle.hello.HelloWorldPlugin;

            public class App {
                public static void main(String[] args) {
                    Project project = ProjectBuilder.builder().build();
                    project.getPlugins().apply(HelloWorldPlugin.class);
                }
            }
        """

        applyPluginWithProjectBuilderDir.file('build.gradle') << """
            apply plugin: 'groovy'

            group = 'org.gradle'
            version = '1.0'

            dependencies {
                compile gradleApi()
                compile 'org.gradle:hello:1.0'
            }

            repositories {
                maven { url '${repoDir.toURI().toURL()}' }
            }

            task runApp(type: JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                main = 'org.gradle.consumer.App'
            }
        """

        then:
        createGradleExecutor(PROJECT_BUILDER_GRADLE_VERSION, applyPluginWithProjectBuilderDir, 'runApp').run()
    }

    /**
     * This test demonstrates a workaround for the problem in Gradle 3.0 or 3.1 by adding the functionality that has now
     * moved to {@link org.gradle.initialization.LegacyTypesUtil} directly to the test.
     */
    def "can use plugin built with 2.x to execute a test on top of ProjectBuilder run with Gradle 3.0 using added LegacyTypesUtil methods"() {
        given:
        def repoDir = publishHelloWorldPluginWithOldGradleVersion()
        TestFile applyPluginWithProjectBuilderDir = temporaryFolder.createDir('apply-plugin-with-project-builder')

        when:
        applyPluginWithProjectBuilderDir.file('src/test/java/org/gradle/consumer/PluginTest.java') << """
            package org.gradle.consumer;

            import org.gradle.api.Project;
            import org.gradle.api.Plugin;
            import org.gradle.testfixtures.ProjectBuilder;
            import org.junit.Before;
            import org.junit.Test;

            import org.gradle.hello.HelloWorldPlugin;

            import org.gradle.api.GradleException;
            import org.gradle.initialization.MixInLegacyTypesClassLoader;
            import org.gradle.internal.classpath.ClassPath;

            import java.io.BufferedReader;
            import java.io.IOException;
            import java.io.InputStreamReader;
            import java.net.URL;
            import java.util.HashSet;
            import java.util.Set;

            public class PluginTest {
                private Project project;

                @Before
                public void setup() {
                    legacyClassPathFix();
                    project = ProjectBuilder.builder().build();
                }

                @Test
                public void canApplyPlugin() {
                    project.getPlugins().apply(HelloWorldPlugin.class);
                }

                private void legacyClassPathFix() {
                    ClassLoader systemClassLoader = getClass().getClassLoader();
                    MixInLegacyTypesClassLoader legacyClassLoader = new MixInLegacyTypesClassLoader(null, ClassPath.EMPTY);
                    try {
                        java.lang.reflect.Method defineClassMethod =
                            ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
                        defineClassMethod.setAccessible(true);

                        java.lang.reflect.Method generateMissingClassMethod =
                            MixInLegacyTypesClassLoader.class.getDeclaredMethod("generateMissingClass", String.class);
                        generateMissingClassMethod.setAccessible(true);

                        java.lang.reflect.Field syntheticClassesField =
                            MixInLegacyTypesClassLoader.class.getDeclaredField("syntheticClasses");
                        syntheticClassesField.setAccessible(true);

                        for (String name : (Set<String>) syntheticClassesField.get(legacyClassLoader)) {
                            byte[] bytes = (byte[]) generateMissingClassMethod.invoke(legacyClassLoader, name);
                            defineClassMethod.invoke(systemClassLoader, name, bytes, 0, bytes.length);
                        }

                        defineClassMethod.setAccessible(false);
                        generateMissingClassMethod.setAccessible(false);
                        syntheticClassesField.setAccessible(false);
                    } catch (Exception e) {
                        throw new GradleException("Error in legacy class path fix", e);
                    }
                }
            }
        """

        applyPluginWithProjectBuilderDir.file('build.gradle') << """
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

        then:
        createGradleExecutor(PROJECT_BUILDER_GRADLE_VERSION_AFFECTED, applyPluginWithProjectBuilderDir, 'test').run()
    }

    private TestFile publishHelloWorldPluginWithOldGradleVersion() {
        TestFile helloWorldPluginDir = temporaryFolder.createDir('hello')
        TestFile repoDir = new TestFile(testDirectory, 'repo')

        helloWorldPluginDir.file('src/main/groovy/org/gradle/hello/HelloWorldPlugin.groovy') << """
            package org.gradle.hello

            import org.gradle.api.Project
            import org.gradle.api.Plugin

            class HelloWorldPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.create('helloWorld', HelloWorld)
                }
            }
        """

        helloWorldPluginDir.file('src/main/groovy/org/gradle/hello/HelloWorld.groovy') << """
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

        helloWorldPluginDir.file('build.gradle') << """
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

        createGradleExecutor(HELLO_WORLD_PLUGIN_GRADLE_VERSION, helloWorldPluginDir, 'publish').run()

        return repoDir
    }

    private GradleExecuter createGradleExecutor(String gradleVersion, File projectDir, String... tasks) {
        if (gradleVersion == '3.2+') {
            new GradleContextualExecuter(new UnderDevelopmentGradleDistribution(), temporaryFolder)
        } else {
            version(buildContext.distribution(gradleVersion)).inDirectory(projectDir).withTasks(tasks)
        }
    }
}
