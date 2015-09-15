/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner

import org.gradle.integtests.fixtures.daemon.DaemonFixture
import org.gradle.integtests.fixtures.daemon.DaemonsFixture
import org.gradle.integtests.fixtures.executer.DaemonGradleExecuter
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.*

class GradleRunnerClasspathIntegrationTest extends AbstractGradleRunnerIntegrationTest {

    @Rule
    TestNameTestDirectoryProvider pluginProjectDir = new TestNameTestDirectoryProvider()

    def "unresolvable plugin for provided empty classpath fails build and indicates searched locations"() {
        given:
        buildFile << pluginDeclaration()

        when:
        GradleRunner gradleRunner = runner('helloWorld')
        gradleRunner.withClasspath([] as List<URI>)
        BuildResult result = gradleRunner.buildAndFail()

        then:
        noExceptionThrown()
        TextUtil.normaliseLineSeparators(result.standardError).contains("""Plugin [id: 'com.company.helloworld'] was not found in any of the following sources:

- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
- Gradle Central Plugin Repository (plugin dependency must include a version number for this source)""")
        result.tasks.collect { it.path } == []
        result.taskPaths(SUCCESS).empty
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
    }

    def "unresolvable plugin for provided classpath fails build and indicates searched classpath"() {
        given:
        buildFile << pluginDeclaration()
        List<URI> pluginClasspath = [file('plugin/classes').toURI(), file('plugin/resources').toURI()]

        when:
        GradleRunner gradleRunner = runner('helloWorld')
        gradleRunner.withClasspath(pluginClasspath)
        BuildResult result = gradleRunner.buildAndFail()

        then:
        noExceptionThrown()
        List<File> expectedClasspath = pluginClasspath.collect { new File(it) }
        result.standardError.contains("Plugin with id 'com.company.helloworld' not found. Searched classpath: $expectedClasspath")
        result.tasks.collect { it.path } == []
        result.taskPaths(SUCCESS).empty
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
    }

    def "can resolve plugin for provided classpath"() {
        given:
        compilePluginProjectSources()
        buildFile << pluginDeclaration()

        when:
        GradleRunner gradleRunner = runner('helloWorld')
        gradleRunner.withClasspath(getPluginClasspath())
        BuildResult result = gradleRunner.build()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloWorld')
        result.standardOutput.contains('Hello world!')
        !result.standardError
        result.tasks.collect { it.path } == [':helloWorld']
        result.taskPaths(SUCCESS) == [':helloWorld']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty

        cleanup:
        killUserDaemon()
    }

    def "can use plugin classes for declared plugin in provided classpath"() {
        given:
        compilePluginProjectSources()
        buildFile << pluginDeclaration()
        buildFile << pluginClassUsage()

        when:
        GradleRunner gradleRunner = runner('helloWorld')
        gradleRunner.withClasspath(getPluginClasspath())
        BuildResult result = gradleRunner.build()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloWorld')
        result.standardOutput.contains('Hello world!')
        !result.standardError
        result.tasks.collect { it.path } == [':helloWorld']
        result.taskPaths(SUCCESS) == [':helloWorld']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty

        cleanup:
        killUserDaemon()
    }

    def "can create enhanced tasks for custom task types in plugin for provided classpath"() {
        given:
        pluginProjectFile('src/main/groovy/org/gradle/test/Messenger.groovy') << """
            package org.gradle.test

            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction
            import org.gradle.api.tasks.Input

            class Messenger extends DefaultTask {
                @Input
                String message

                @TaskAction
                void doSomething() {
                    println message
                }
            }
        """

        compilePluginProjectSources()
        buildFile << pluginDeclaration()
        buildFile << """
            import org.gradle.test.Messenger

            task helloMessage(type: Messenger) {
                message = 'Hello message'
            }

            model {
                tasks {
                    byeMessage(Messenger) {
                        message = 'Bye message'
                    }
                }
            }
        """

        when:
        GradleRunner gradleRunner = runner('helloMessage', 'byeMessage')
        gradleRunner.withClasspath(getPluginClasspath())
        BuildResult result = gradleRunner.build()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloMessage')
        result.standardOutput.contains(':byeMessage')
        result.standardOutput.contains('Hello message')
        result.standardOutput.contains('Bye message')
        !result.standardError
        result.tasks.collect { it.path } == [':helloMessage', ':byeMessage']
        result.taskPaths(SUCCESS) == [':helloMessage', ':byeMessage']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty

        cleanup:
        killUserDaemon()
    }

    @Unroll
    def "can resolve plugin for provided classpath that applies another plugin under test by #type"() {
        given:
        pluginProjectFile('src/main/groovy/org/gradle/test/CompositePlugin.groovy') << """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class CompositePlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.apply(plugin: $notation)
                }
            }
        """

        pluginProjectFile('src/main/resources/META-INF/gradle-plugins/com.company.composite.properties') << """
            implementation-class=org.gradle.test.CompositePlugin
        """

        compilePluginProjectSources()
        buildFile << pluginDeclaration('com.company.composite')

        when:
        GradleRunner gradleRunner = runner('helloWorld')
        gradleRunner.withClasspath(getPluginClasspath())
        BuildResult result = gradleRunner.build()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloWorld')
        result.standardOutput.contains('Hello world!')
        !result.standardError
        result.tasks.collect { it.path } == [':helloWorld']
        result.taskPaths(SUCCESS) == [':helloWorld']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty

        cleanup:
        killUserDaemon()

        where:
        type         | notation
        'class type' | 'HelloWorldPlugin'
        'identifier' | "'com.company.helloworld'"
    }

    def "cannot use plugin classes for undeclared plugin in provided classpath"() {
        given:
        compilePluginProjectSources()
        buildFile << pluginClassUsage()

        when:
        GradleRunner gradleRunner = runner('helloWorld')
        gradleRunner.withClasspath(getPluginClasspath())
        BuildResult result = gradleRunner.buildAndFail()

        then:
        noExceptionThrown()
        result.standardError.contains('unable to resolve class org.gradle.test.Support')
        result.tasks.collect { it.path } == []
        result.taskPaths(SUCCESS).empty
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty

        cleanup:
        killUserDaemon()
    }

    private void killUserDaemon() {
        DaemonsFixture userDaemonFixture = daemons(testKitWorkspace)
        onlyDaemon(userDaemonFixture).kill()
    }

    private DaemonFixture onlyDaemon(DaemonsFixture daemons) {
        List<DaemonFixture> userDaemons = daemons.visible
        assert userDaemons.size() == 1
        userDaemons[0]
    }

    private String pluginDeclaration(String pluginIdentifier = 'com.company.helloworld') {
        """
        plugins {
            id '$pluginIdentifier'
        }
        """
    }

    private String pluginClassUsage() {
        """
        import org.gradle.test.Support

        task byeWorld {
            doLast {
                println Support.MSG
            }
        }
        """
    }

    private void compilePluginProjectSources() {
        createPluginProjectSourceFiles()

        new DaemonGradleExecuter(new UnderDevelopmentGradleDistribution(), pluginProjectDir)
            .usingProjectDirectory(pluginProjectDir.testDirectory)
            .withGradleUserHomeDir(testKitWorkspace)
            .withDaemonBaseDir(testKitWorkspace.file('daemon'))
            .withArguments('classes')
            .run()
    }

    private void createPluginProjectSourceFiles() {
        pluginProjectFile('src/main/groovy/org/gradle/test/HelloWorldPlugin.groovy') << """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class HelloWorldPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.task('helloWorld', type: HelloWorld)
                }
            }
        """

        pluginProjectFile('src/main/groovy/org/gradle/test/HelloWorld.groovy') << """
            package org.gradle.test

            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction

            class HelloWorld extends DefaultTask {
                @TaskAction
                void doSomething() {
                    println 'Hello world!'
                }
            }
        """

        pluginProjectFile('src/main/groovy/org/gradle/test/Support.groovy') << """
            package org.gradle.test

            class Support {
                public static String MSG = 'Bye world!'
            }
        """

        pluginProjectFile('src/main/resources/META-INF/gradle-plugins/com.company.helloworld.properties') << """
            implementation-class=org.gradle.test.HelloWorldPlugin
        """

        pluginProjectFile('build.gradle') << """
            apply plugin: 'groovy'

            dependencies {
                compile gradleApi()
                compile localGroovy()
            }
        """
    }

    private TestFile pluginProjectFile(String path) {
        pluginProjectDir.file(path)
    }

    private List<URI> getPluginClasspath() {
        [pluginProjectDir.file('build/classes/main').toURI(), pluginProjectDir.file('build/resources/main').toURI()]
    }
}
