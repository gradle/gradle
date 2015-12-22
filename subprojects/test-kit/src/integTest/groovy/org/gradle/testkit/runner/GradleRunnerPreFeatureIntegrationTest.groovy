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
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.fixtures.annotations.Debug
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.fixtures.FeatureCompatibility.CAPTURE_BUILD_OUTPUT_IN_DEBUG
import static org.gradle.testkit.runner.fixtures.FeatureCompatibility.PLUGIN_CLASSPATH_INJECTION
import static org.gradle.testkit.runner.fixtures.GradleRunnerCompatibilityIntegTestRunner.TESTKIT_MIN_SUPPORTED_VERSION

class GradleRunnerPreFeatureIntegrationTest extends AbstractGradleRunnerIntegrationTest {

    private static final ReleasedVersionDistributions RELEASED_VERSION_DISTRIBUTIONS = new ReleasedVersionDistributions()

    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    def "build result does not capture tasks when executed with unsupported target gradle version"() {
        String maxUnsupportedVersion = getMaxUnsupportedVersion(TESTKIT_MIN_SUPPORTED_VERSION)
        String minSupportedVersion = TESTKIT_MIN_SUPPORTED_VERSION.version

        given:
        buildFile << helloWorldTask()

        when:
        runner('helloWorld')
            .withGradleVersion(maxUnsupportedVersion)
            .build()

        then:
        def e = thrown InvalidRunnerConfigurationException
        e.cause instanceof UnsupportedVersionException
        e.cause.message == "The version of Gradle you are using ($maxUnsupportedVersion) does not support executing builds with TestKit. Support for this is available in Gradle $minSupportedVersion and all later versions."
    }

    @Debug
    def "build result output is not captured when executed with unsupported target gradle version in debug mode"() {
        given:
        buildFile << helloWorldTask()

        when:
        def result = runner('helloWorld')
                .withGradleVersion(getMaxUnsupportedVersion(CAPTURE_BUILD_OUTPUT_IN_DEBUG.since))
                .build()

        then:
        result.task(":helloWorld").outcome == SUCCESS
        result.output.contains(':helloWorld')
        !result.output.contains('Hello world!')
    }

    def "cannot use plugin classpath feature when executed with unsupported target gradle version"() {
        String maxUnsupportedVersion = getMaxUnsupportedVersion(PLUGIN_CLASSPATH_INJECTION.since)
        String minSupportedVersion = PLUGIN_CLASSPATH_INJECTION.since.version

        given:
        buildFile << pluginDeclaration()

        when:
        runner('helloWorld')
            .withGradleVersion(maxUnsupportedVersion)
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        def e = thrown InvalidRunnerConfigurationException
        e.cause instanceof UnsupportedVersionException
        e.cause.message == "The version of Gradle you are using ($maxUnsupportedVersion) does not support the plugin classpath injection feature used by GradleRunner. Support for this is available in Gradle $minSupportedVersion and all later versions."
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
            apply plugin: 'com.company.helloworld'
        """

        when:
        def result = runner('helloWorld')
                .withGradleVersion(getMaxUnsupportedVersion(PLUGIN_CLASSPATH_INJECTION.since))
                .forwardOutput()
                .build()

        then:
        result.task(":helloWorld").outcome == SUCCESS
    }

    private String getMaxUnsupportedVersion(GradleVersion minSupportedVersion) {
        RELEASED_VERSION_DISTRIBUTIONS.getPrevious(minSupportedVersion).version.version
    }

    private void compilePluginProjectSources() {
        createPluginProjectSourceFiles()
        new ForkingGradleExecuter(new UnderDevelopmentGradleDistribution(), testProjectDir)
                .withArguments('classes', "--no-daemon")
                .run()
    }

    static String pluginDeclaration() {
        """
        plugins {
            id 'com.company.helloworld'
        }
        """
    }

    private void createPluginProjectSourceFiles() {
        pluginProjectFile("src/main/groovy/org/gradle/test/HelloWorldPlugin.groovy") << """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class HelloWorldPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.task('helloWorld', type: HelloWorld)
                }
            }
        """

        pluginProjectFile("src/main/groovy/org/gradle/test/HelloWorld.groovy") << """
            package org.gradle.test

            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction

            class HelloWorld extends DefaultTask {
                @TaskAction
                void doSomething() {
                    logger.quiet 'Hello world!'
                }
            }
        """

        pluginProjectFile("src/main/resources/META-INF/gradle-plugins/com.company.helloworld.properties") << """
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
        testProjectDir.file(path)
    }

    private List<File> getPluginClasspath() {
        [pluginProjectFile("build/classes/main"), pluginProjectFile('build/resources/main')]
    }
}
