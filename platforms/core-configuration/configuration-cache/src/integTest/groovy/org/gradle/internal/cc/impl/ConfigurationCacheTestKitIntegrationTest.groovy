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

package org.gradle.internal.cc.impl

import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.gradle.test.preconditions.OsTestPreconditions

import org.gradle.testing.jacoco.plugins.fixtures.JacocoReportXmlFixture
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.internal.TextUtil
import spock.lang.Issue
import spock.lang.TempDir

import static org.gradle.integtests.fixtures.logging.ConfigurationCacheOutputNormalizer.PROMO_PREFIX

@Requires(OsTestPreconditions.NotWindows)
class ConfigurationCacheTestKitIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @TempDir
    File jacocoDestinationDir

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
        runner.withGradleInstallation(buildContext.gradleHomeDir)
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
        runner.withGradleInstallation(buildContext.gradleHomeDir)
        runner.forwardOutput()
        runner.withProjectDir(testDirectory)
        runner.withPluginClasspath([new File("some-dir")])
        result = runner.build()
        output = result.output

        then:
        !output.contains("configuration cache")
        !output.contains(PROMO_PREFIX)
    }

    @Issue("https://github.com/gradle/gradle/issues/23817")
    @Issue("https://github.com/gradle/gradle/issues/27956")
    def "configuration cache tracks agent-instrumented plugin input with debug=#debug and pluginClasspath=#pluginClasspath"() {
        given:
        settingsFile.text = "rootProject.name = 'plugin-under-test'"
        buildFile << """
            plugins {
                id 'groovy'
                id 'java-gradle-plugin'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useSpock()
                    }
                }
            }

            gradlePlugin {
                plugins {
                    register('myPlugin') {
                        id = 'org.example.my'
                        implementationClass = 'MyPlugin'
                    }
                }
            }

            tasks.jar {
                archiveBaseName = 'plugin'
                archiveVersion = '1.0'
            }

            tasks.named('test') {
                dependsOn(tasks.named('jar'))
            }
        """
        file("src/main/java/MyPlugin.java") << """
            import org.gradle.api.*;

            public class MyPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    String returned = System.getProperty("my.property");
                    System.out.println("returned = " + returned);
                }
            }
        """

        def pluginJarPath = testDirectory.file("build/libs/plugin-1.0.jar").absolutePath
        def innerBuildFileBody = pluginClasspath
            ? "plugins { id 'org.example.my' }"
            : "buildscript { dependencies { classpath(files('${pluginJarPath}')) } }\napply plugin: MyPlugin"
        def maybeWithPluginClasspath = pluginClasspath ? "runner = runner.withPluginClasspath()" : ""

        file("src/test/groovy/InnerInstrumentationTest.groovy") << """
            import org.gradle.testkit.runner.GradleRunner
            import spock.lang.Specification
            import java.nio.file.Files

            class InnerInstrumentationTest extends Specification {
                def "agent-instrumented input is recorded in the CC report"() {
                    given:
                    def projectDir = Files.createTempDirectory('inner').toFile()
                    new File(projectDir, 'settings.gradle').text = "rootProject.name = 'inner'"
                    new File(projectDir, 'build.gradle').text = '''${innerBuildFileBody}'''

                    when:
                    def runner = GradleRunner.create()
                        .withProjectDir(projectDir)
                        .withArguments('--configuration-cache', '-Dmy.property=my.value', 'help')
                        .withDebug(${debug})
                        .forwardOutput()
                    ${maybeWithPluginClasspath}
                    def result = runner.build()

                    then:
                    result.output.contains('returned = my.value')
                    result.output.contains('Configuration cache entry stored.')

                    and: 'the CC report records the system property as an input attributed to the plugin'
                    def reportHtml = new File(projectDir, 'build/reports/configuration-cache')
                        .listFiles().toList()
                        .collectMany { it.listFiles().toList() }
                        .collect { new File(it, 'configuration-cache-report.html') }
                        .find { it.exists() }
                    reportHtml != null
                    def report = reportHtml.text
                    report.contains('"name":"my.property"')
                    // buildscript {} || plugins {}
                    report.contains("plugin class 'MyPlugin'") || report.contains("plugin 'org.example.my'")
                }
            }
        """

        expect:
        succeeds('test')

        where:
        debug | pluginClasspath
        true  | false
        true  | true
        false | false
        false | true
    }

    /**
     * This test check that Jacoco works with TestKit when configuration cache is DISABLED.
     * We don't have support for that case when configuration cache is enabled yet.
     *
     * But we broke --no-configuration-cache case already twice in the past, so it's worth testing it.
     */
    @Issue(["https://github.com/gradle/gradle/issues/13614", "https://github.com/gradle/gradle/issues/28729"])
    @Requires(value = TestExecutionPreconditions.NotEmbeddedExecutor, reason = "Testing build using a TestKit")
    def "running a test that applies Jacoco with TestKit should generate a test report when running without configuration cache"() {
        given:
        // Setting Jacoco destination dir to non-ascii location causes some problems,
        // so let's write to a temporary directory without non-ascii characters
        def jacocoDestinationFile = TextUtil.normaliseFileSeparators("${jacocoDestinationDir.absolutePath}/jacoco.exec")
        buildFile << """
            plugins {
                id 'java'
                id 'groovy'
                id 'jacoco'
                id 'java-gradle-plugin'
            }

            ${mavenCentralRepository()}

            configurations.create("jacocoRuntime") {
                canBeResolved = true
                canBeConsumed = false
            }

            dependencies {
                jacocoRuntime("org.jacoco:org.jacoco.agent:\${JacocoPlugin.DEFAULT_JACOCO_VERSION}:runtime")
            }

            testing {
                suites {
                    test {
                        useSpock("2.3-groovy-4.0")
                    }
                }
            }

            test {
                test.extensions.getByType(JacocoTaskExtension).destinationFile = new File("$jacocoDestinationFile")
                systemProperty "jacocoAgentJar", configurations.jacocoRuntime.singleFile.absolutePath
                systemProperty "jacocoDestFile", test.extensions.getByType(JacocoTaskExtension).destinationFile.absolutePath
            }

            gradlePlugin {
                plugins {
                    create("my-plugin") {
                        id = "test.my-plugin"
                        implementationClass = "test.gradle.MyPlugin"
                        version = "1.0.0"
                    }
                }
            }

            tasks.jacocoTestReport {
                reports {
                    xml.required = true
                }
            }

            tasks.jacocoTestCoverageVerification {
                violationRules {
                    rule {
                        limit {
                            minimum = 1.0
                        }
                    }
                }
                dependsOn(tasks.test)
                dependsOn(tasks.jacocoTestReport)
            }
        """
        file("src/main/java/test/gradle/MyPlugin.java") << """
            package test.gradle;
            import org.gradle.api.*;

            public class MyPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    project.task("testTask").doFirst(task -> {
                        System.out.println("Test task");
                    });
                }
            }
        """
        file("src/test/groovy/Test.groovy") << """
            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import spock.lang.Specification
            import spock.lang.TempDir

            class Test extends Specification {
                @TempDir
                File testProjectDir
                File buildFile
                File gradleProperties

                def setup() {
                    new File(testProjectDir, 'settings.gradle') << "rootProject.name = 'test'"
                    buildFile = new File(testProjectDir, 'build.gradle')
                    gradleProperties = new File(testProjectDir, 'gradle.properties')
                }

                def "run Gradle build with Jacoco"() {
                    given:
                    def jacocoAgentJar = System.getProperty("jacocoAgentJar")
                    def jacocoDestFile = System.getProperty("jacocoDestFile")
                    buildFile << "plugins { id('test.my-plugin') }"
                    gradleProperties << "org.gradle.jvmargs=\\"-javaagent:\$jacocoAgentJar=destfile=\$jacocoDestFile\\""

                    when:
                    def result = GradleRunner.create()
                        .withProjectDir(testProjectDir)
                        .withArguments("testTask", "--no-configuration-cache")
                        .withPluginClasspath()
                        .withDebug(false)
                        .forwardOutput()
                        .build()

                    then:
                    noExceptionThrown()
                }
            }
        """

        when:
        succeeds("jacocoTestCoverageVerification")

        then:
        def report = new JacocoReportXmlFixture(file("build/reports/jacoco/test/jacocoTestReport.xml"))
        report.assertHasClassCoverage("test.gradle.MyPlugin")
    }
}
