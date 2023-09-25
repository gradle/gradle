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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testkit.runner.GradleRunner

@Requires(UnitTestPreconditions.NotWindows)
class ConfigurationCacheTestkitIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
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
}
