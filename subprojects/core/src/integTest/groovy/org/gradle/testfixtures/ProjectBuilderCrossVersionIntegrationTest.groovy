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
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import spock.lang.Issue

import static org.gradle.util.TestPrecondition.JDK8_OR_EARLIER

@Issue("GRADLE-3558")
@TargetVersions(['2.0', '2.1', '2.2', '2.3', '2.4', '2.5', '2.6', '2.7'])
@Requires(JDK8_OR_EARLIER) //Versions <2.10 fail to compile the plugin with Java 9 (Could not determine java version from '9-ea')
class ProjectBuilderCrossVersionIntegrationTest extends MultiVersionIntegrationSpec {

    public static final List<String> PROJECT_BUILDER_GRADLE_VERSIONS_AFFECTED = ['3.0', '3.1']

    private final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    private final List<GradleExecuter> executers = []
    private File repoDir

    def setup() {
        repoDir = file('repo')
        publishHelloWorldPluginWithOldGradleVersion()
    }

    def cleanup() {
        executers.each { it.cleanup() }
    }

    def "can apply 2.x plugin using ProjectBuilder in a test running with Gradle 3.2+"() {
        when:
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

        buildFile << """
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
        run('test')
    }

    def "can apply 2.x plugin using ProjectBuilder in a Java application running with Gradle 3.2+"() {
        when:
        file('src/main/java/org/gradle/consumer/App.java') << """
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

        buildFile << """
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
        run('runApp')
    }

    def "can not apply 2.x plugin using ProjectBuilder in a test running with Gradle 3.0 or 3.1"() {
        when:
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

        then:
        PROJECT_BUILDER_GRADLE_VERSIONS_AFFECTED.each {
            def executionFailure = createGradleExecutor(it, 'test').runWithFailure()
            executionFailure.assertTestsFailed()
            executionFailure.assertOutputContains('Caused by: java.lang.ClassNotFoundException at PluginTest.java:21')
        }
    }

    /**
     * This test demonstrates a workaround for the problem in Gradle 3.0 or 3.1 by adding the functionality that has now
     * moved to {@link org.gradle.initialization.DefaultLegacyTypesSupport} directly to the test.
     */
    def "can apply 2.x plugin using ProjectBuilder in a test running with Gradle 3.0 or 3.1 using added DefaultLegacyTypesSupport functionality"() {
        when:
        file('src/test/java/org/gradle/consumer/PluginTest.java') << """
            package org.gradle.consumer;

            import org.gradle.api.Project;
            import org.gradle.api.Plugin;
            import org.gradle.testfixtures.ProjectBuilder;
            import org.junit.Before;
            import org.junit.Test;

            import org.gradle.hello.HelloWorldPlugin;

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
                public void setup() throws Exception {
                    legacyClassPathFix();
                    project = ProjectBuilder.builder().build();
                }

                @Test
                public void canApplyPlugin() {
                    project.getPlugins().apply(HelloWorldPlugin.class);
                }

                private void legacyClassPathFix() throws Exception {
                    ClassLoader systemClassLoader = getClass().getClassLoader();
                    MixInLegacyTypesClassLoader legacyClassLoader = new MixInLegacyTypesClassLoader(null, ClassPath.EMPTY);

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
                }
            }
        """

        buildFile << """
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
        PROJECT_BUILDER_GRADLE_VERSIONS_AFFECTED.each {
            createGradleExecutor(it, 'test').run()
        }
    }

    private void publishHelloWorldPluginWithOldGradleVersion() {
        TestFile helloWorldPluginDir = temporaryFolder.createDir('hello')

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

        createGradleExecutor(version, helloWorldPluginDir, 'publish').run()
    }

    private GradleExecuter createGradleExecutor(String gradleVersion, File projectDir = testDirectory, String... tasks) {
        def executer = buildContext.distribution(gradleVersion).executer(temporaryFolder)
        executer.inDirectory(projectDir)
        executer.withTasks(tasks)
        executers << executer
        executer
    }
}
