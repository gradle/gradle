/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Issue

@Requires(UnitTestPreconditions.NotWindows)
class ConfigurationCacheTestKitIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "reports when a TestKit build runs with a Java agent and configuration caching enabled"() {
        def builder = artifactBuilder()
        builder.sourceFile("TestAgent.java") << """
            public class TestAgent {
                public static void premain(String p1, java.lang.instrument.Instrumentation p2) {
                }
            }
        """
        builder.manifestAttributes("Premain-Class": "TestAgent")
        def agentJar = file("agent.jar")
        builder.buildJar(agentJar)
        buildFile << """
        """
        settingsFile << """
        """

        when:
        def runner = GradleRunner.create()
        runner.withJvmArguments("-javaagent:${agentJar}")
        if (!GradleContextualExecuter.embedded) {
            runner.withGradleInstallation(buildContext.gradleHomeDir)
        }
        runner.withArguments("--configuration-cache")
        runner.forwardOutput()
        runner.withProjectDir(testDirectory)
        runner.withPluginClasspath([new File("some-dir")])
        def result = runner.buildAndFail()
        def output = result.output

        then:
        output.contains("- Gradle runtime: support for using a Java agent with TestKit builds is not yet implemented with the configuration cache.")

        when:
        runner = GradleRunner.create()
        runner.withJvmArguments("-javaagent:${agentJar}")
        if (!GradleContextualExecuter.embedded) {
            runner.withGradleInstallation(buildContext.gradleHomeDir)
        }
        runner.forwardOutput()
        runner.withProjectDir(testDirectory)
        result = runner.build()
        output = result.output

        then:
        !output.contains("configuration cache")
    }

    @Issue("https://github.com/gradle/gradle/issues/27956")
    def "dependencies of builds tested with TestKit in debug mode are instrumented and violations are reported"() {
        given:
        file("included/src/main/java/MyPlugin.java") << """
            import org.gradle.api.*;

            public class MyPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    String returned = System.getProperty("my.property");
                    System.out.println("returned = " + returned);
                }
            }
        """
        file("included/build.gradle") << """plugins { id("java-gradle-plugin") }"""
        file("included/settings.gradle") << "rootProject.name = 'included'"
        buildFile.text = """
            buildscript {
                dependencies {
                    classpath(files("./included/build/libs/included.jar"))
                }
            }
            apply plugin: MyPlugin
        """
        settingsFile << """rootProject.name = 'root'"""

        when:
        executer.inDirectory(file("included")).withTasks("jar").run()
        def runner = GradleRunner.create()
            .withDebug(true)
            .withArguments("--configuration-cache", "-Dmy.property=my.value", "-i")
            .forwardOutput()
            .withProjectDir(testDirectory)
        if (!GradleContextualExecuter.embedded) {
            runner.withGradleInstallation(buildContext.gradleHomeDir)
        }
        def result = runner.build()

        then:
        def output = result.output
        problems.assertResultHasProblems(OutputScrapingExecutionResult.from(output, "")) {
            withInput("Plugin class 'MyPlugin': system property 'my.property'")
            ignoringUnexpectedInputs()
        }
        output.contains("Configuration cache entry stored.")
    }
}
