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

import org.gradle.integtests.fixtures.executer.ForkingGradleExecuter
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.fixtures.Debug
import org.gradle.tooling.UnsupportedVersionException

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class GradleRunnerPreFeatureIntegrationTest extends AbstractGradleRunnerIntegrationTest {

    def "cannot use feature when target gradle version is < 2.8"() {
        given:
        buildFile << pluginDeclaration()

        when:
        runner('helloWorld1')
            .withGradleVersion("2.7")
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        def e = thrown InvalidRunnerConfigurationException
        e.cause instanceof UnsupportedVersionException
        e.cause.message == "The version of Gradle you are using (2.7) does not support the plugin classpath injection feature used by GradleRunner. Support for this is available in Gradle 2.8 and all later versions."
    }

    def "can use manual injection with older versions that do not support injection"() {
        given:
        compilePluginProjectSources()
        buildFile << """
            buildscript {
                dependencies {
                    classpath files(${pluginClasspath.collect { "'${it.absolutePath.replace("\\", "\\\\")}'" }.join(", ")})
                }
            }
            apply plugin: 'com.company.helloworld1'
        """

        when:
        def result = runner('helloWorld1')
                .withGradleVersion("2.7")
                .forwardOutput()
                .build()

        then:
        result.task(":helloWorld1").outcome == SUCCESS
    }

    @Debug
    def "build result output is not captured if executed in debug mode and targets gradle version is < 2.9"() {
        given:
        buildFile << helloWorldTask()

        when:
        def result = runner('helloWorld')
                .withGradleVersion('2.8')
                .build()

        then:
        result.task(":helloWorld").outcome == SUCCESS
        result.output.contains(':helloWorld')
        !result.output.contains('Hello world!')
    }

    private void compilePluginProjectSources(int counter = 1) {
        createPluginProjectSourceFiles(counter)
        new ForkingGradleExecuter(new UnderDevelopmentGradleDistribution(), testProjectDir)
                .usingProjectDirectory(file(counter.toString()))
                .withArguments('classes', "--no-daemon")
                .run()
    }

    static String pluginDeclaration(int counter = 1) {
        """
        plugins {
            id 'com.company.helloworld$counter'
        }
        """
    }

    private void createPluginProjectSourceFiles(int counter = 1) {
        pluginProjectFile(counter, "src/main/groovy/org/gradle/test/HelloWorldPlugin${counter}.groovy") << """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class HelloWorldPlugin${counter} implements Plugin<Project> {
                void apply(Project project) {
                    project.task('helloWorld${counter}', type: HelloWorld${counter})
                }
            }
        """

        pluginProjectFile(counter, "src/main/groovy/org/gradle/test/HelloWorld${counter}.groovy") << """
            package org.gradle.test

            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction

            class HelloWorld${counter} extends DefaultTask {
                @TaskAction
                void doSomething() {
                    logger.quiet 'Hello world! (${counter})'
                }
            }
        """

        pluginProjectFile(counter, "src/main/resources/META-INF/gradle-plugins/com.company.helloworld${counter}.properties") << """
            implementation-class=org.gradle.test.HelloWorldPlugin${counter}
        """

        pluginProjectFile(counter, 'build.gradle') << """
            apply plugin: 'groovy'

            dependencies {
                compile gradleApi()
                compile localGroovy()
            }
        """
    }

    private TestFile pluginProjectFile(int counter = 1, String path) {
        testProjectDir.file(counter.toString()).file(path)
    }

    private List<File> getPluginClasspath(int counter = 1) {
        [pluginProjectFile(counter, "build/classes/main"), pluginProjectFile(counter, 'build/resources/main')]
    }
}
