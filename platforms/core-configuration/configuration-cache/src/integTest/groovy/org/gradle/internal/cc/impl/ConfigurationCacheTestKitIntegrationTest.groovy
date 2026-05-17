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

@Requires(OsTestPreconditions.NotWindows)
class ConfigurationCacheTestKitIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @TempDir
    File jacocoDestinationDir

    private File buildStubAgentJar() {
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
        agentJar
    }

    @Issue("https://github.com/gradle/gradle/issues/25929")
    def "third-party Java agent without a transformer does not cause a configuration-cache problem"() {
        given:
        def agentJar = buildStubAgentJar()
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
        def result = runner.build()
        def output = result.output

        then:
        !output.contains("third-party Java agents with inactive Gradle's instrumentation agent are not supported by the configuration cache")
        !output.contains("Configuration cache problems found")
    }

    def "third-party Java agent with Gradle's instrumentation agent disabled is reported as a CC problem"() {
        given:
        def agentJar = buildStubAgentJar()
        buildFile << ""
        settingsFile << ""

        when:
        def runner = GradleRunner.create()
        runner.withJvmArguments("-javaagent:${agentJar}")
        runner.withGradleInstallation(buildContext.gradleHomeDir)
        runner.withArguments("--configuration-cache", "-Dorg.gradle.internal.instrumentation.agent=false")
        runner.forwardOutput()
        runner.withProjectDir(testDirectory)
        runner.withPluginClasspath([new File("some-dir")])
        def result = runner.buildAndFail()
        def output = result.output

        then:
        output.contains("third-party Java agents with inactive Gradle's instrumentation agent are not supported by the configuration cache")
    }

    def "third-party Java agent with Gradle's instrumentation agent disabled without CC succeeds"() {
        given:
        def agentJar = buildStubAgentJar()
        buildFile << ""
        settingsFile << ""

        when:
        def runner = GradleRunner.create()
        runner.withJvmArguments("-javaagent:${agentJar}")
        runner.withGradleInstallation(buildContext.gradleHomeDir)
        runner.withArguments("-Dorg.gradle.internal.instrumentation.agent=false")
        runner.forwardOutput()
        runner.withProjectDir(testDirectory)
        runner.withPluginClasspath([new File("some-dir")])
        def result = runner.build()
        def output = result.output

        then:
        !output.contains("third-party Java agents with inactive Gradle's instrumentation agent are not supported by the configuration cache")
        !output.contains("Configuration cache problems found")
    }

    def "configuration cache without any Java agent succeeds without problem"() {
        given:
        buildFile << ""
        settingsFile << ""

        when:
        def runner = GradleRunner.create()
        runner.withGradleInstallation(buildContext.gradleHomeDir)
        runner.withArguments("--configuration-cache")
        runner.forwardOutput()
        runner.withProjectDir(testDirectory)
        runner.withPluginClasspath([new File("some-dir")])
        def result = runner.build()
        def output = result.output

        then:
        !output.contains("Configuration cache problems found")
    }

    private File buildTransformerAgentJar() {
        def builder = artifactBuilder()
        builder.sourceFile("TestAgent.java") << """
            import java.lang.instrument.ClassFileTransformer;
            import java.lang.instrument.Instrumentation;
            import java.security.ProtectionDomain;

            public class TestAgent {
                public static void premain(String p1, Instrumentation inst) {
                    inst.addTransformer(new ClassFileTransformer() {
                        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                                 ProtectionDomain pd, byte[] classfileBuffer) {
                            return null;
                        }
                    });
                }
            }
        """
        builder.manifestAttributes("Premain-Class": "TestAgent")
        def agentJar = file("transformer-agent.jar")
        builder.buildJar(agentJar)
        agentJar
    }

    def "third-party Java agent that registers a class-file transformer does not cause a configuration-cache problem"() {
        given:
        def agentJar = buildTransformerAgentJar()
        buildFile << ""
        settingsFile << ""

        when:
        def runner = GradleRunner.create()
        runner.withJvmArguments("-javaagent:${agentJar}")
        runner.withGradleInstallation(buildContext.gradleHomeDir)
        runner.withArguments("--configuration-cache")
        runner.forwardOutput()
        runner.withProjectDir(testDirectory)
        runner.withPluginClasspath([new File("some-dir")])
        def result = runner.build()
        def output = result.output

        then:
        !output.contains("Configuration cache problems found")
        !output.contains("third-party Java agents with inactive Gradle's instrumentation agent are not supported by the configuration cache")
    }

    def "JDWP debug agent does not cause a configuration-cache problem"() {
        given:
        buildFile << ""
        settingsFile << ""

        when:
        def runner = GradleRunner.create()
        runner.withJvmArguments("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:0")
        runner.withGradleInstallation(buildContext.gradleHomeDir)
        runner.withArguments("--configuration-cache")
        runner.forwardOutput()
        runner.withProjectDir(testDirectory)
        runner.withPluginClasspath([new File("some-dir")])
        def result = runner.build()
        def output = result.output

        then:
        !output.contains("Configuration cache problems found")
    }

    private File buildFileWritingAgentJar() {
        def builder = artifactBuilder()
        builder.sourceFile("TestAgent.java") << """
            import java.io.FileWriter;
            import java.lang.instrument.Instrumentation;

            public class TestAgent {
                public static void premain(String args, Instrumentation inst) throws Exception {
                    String destFile = args.startsWith("destfile=") ? args.substring("destfile=".length()) : args;
                    try (FileWriter w = new FileWriter(destFile)) {
                        w.write("agent-ran");
                    }
                }
            }
        """
        builder.manifestAttributes("Premain-Class": "TestAgent")
        def agentJar = file("file-writing-agent.jar")
        builder.buildJar(agentJar)
        agentJar
    }

    def "third-party Java agent that writes a file from premain runs under CC"() {
        given:
        def agentJar = buildFileWritingAgentJar()
        def destFile = file("agent-output.txt")
        buildFile << """
            tasks.register("noop")
        """
        settingsFile << ""

        when:
        def runner = GradleRunner.create()
        runner.withJvmArguments("-javaagent:${agentJar}=destfile=${destFile.absolutePath}")
        runner.withGradleInstallation(buildContext.gradleHomeDir)
        runner.withArguments("--configuration-cache", "noop")
        runner.forwardOutput()
        runner.withProjectDir(testDirectory)
        runner.withPluginClasspath([new File("some-dir")])
        def result = runner.build()
        def output = result.output

        then:
        !output.contains("Configuration cache problems found")
        destFile.exists()
        destFile.length() > 0
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
            .withGradleInstallation(buildContext.gradleHomeDir)
        def result = runner.build()

        then:
        def output = result.output
        problems.assertResultHasProblems(OutputScrapingExecutionResult.from(output, "")) {
            withInput("Plugin class 'MyPlugin': system property 'my.property'")
            ignoringUnexpectedInputs()
        }
        output.contains("Configuration cache entry stored.")
    }

    private void setUpJacocoTestKitProject(String innerCcArgument) {
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
                        useSpock("2.4-groovy-4.0")
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
                        .withArguments("testTask", "$innerCcArgument")
                        .withPluginClasspath()
                        .withDebug(false)
                        .forwardOutput()
                        .build()

                    then:
                    noExceptionThrown()
                }
            }
        """
    }

    /**
     * This test check that Jacoco works with TestKit when configuration cache is DISABLED.
     *
     * We broke --no-configuration-cache case already twice in the past, so it's worth testing it.
     */
    @Issue(["https://github.com/gradle/gradle/issues/13614", "https://github.com/gradle/gradle/issues/28729"])
    @Requires(value = TestExecutionPreconditions.NotEmbeddedExecutor, reason = "Testing build using a TestKit")
    def "running a test that applies Jacoco with TestKit should generate a test report when running without configuration cache"() {
        given:
        setUpJacocoTestKitProject("--no-configuration-cache")

        when:
        succeeds("jacocoTestCoverageVerification")

        then:
        def report = new JacocoReportXmlFixture(file("build/reports/jacoco/test/jacocoTestReport.xml"))
        report.assertHasClassCoverage("test.gradle.MyPlugin")
        report.assertMethodHasLineCoverage("test.gradle.MyPlugin", "apply")
    }

    @Issue("https://github.com/gradle/gradle/issues/25979")
    @Requires(value = TestExecutionPreconditions.NotEmbeddedExecutor, reason = "Testing build using a TestKit")
    def "running a test that applies Jacoco with TestKit should generate a test report when running with configuration cache"() {
        given:
        setUpJacocoTestKitProject("--configuration-cache")

        when:
        succeeds("jacocoTestCoverageVerification")

        then:
        def report = new JacocoReportXmlFixture(file("build/reports/jacoco/test/jacocoTestReport.xml"))
        report.assertHasClassCoverage("test.gradle.MyPlugin")
        report.assertMethodHasLineCoverage("test.gradle.MyPlugin", "apply")
    }
}
