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

@Requires(
    value = OsTestPreconditions.NotWindows,
    reason = "TestKit tests that attach a custom -javaagent: to the inner daemon are flaky on Windows"
)
class ConfigurationCacheTestKitIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    private static final String JAVA_AGENT_PROBLEM_MESSAGE = "third-party Java agents with inactive Gradle's instrumentation agent are not supported by the configuration cache"

    @TempDir
    File jacocoDestinationDir

    /**
     * How the plugin under test reaches the inner build:
     *
     *  - INJECTED_CLASSPATH goes through {@code DefaultInjectedClasspathPluginResolver}, exercised by
     *    TestKit's {@code withPluginClasspath()}.
     *  - MAVEN_REPO goes through {@code DefaultScriptClassPathResolver}, exercised by publishing the
     *    plugin to a local Maven repository and applying it via the {@code plugins {}} block.
     *
     * Tests that don't actually resolve a plugin still use the mode to control whether TestKit attaches
     * the injected classpath: that is what triggers {@code DefaultInjectedClasspathPluginResolver}
     * construction and, by side effect, {@code InjectedClasspathInstrumentationStrategy.getTransform()}.
     */
    private enum PluginResolutionMode {
        INJECTED_CLASSPATH,
        MAVEN_REPO
    }

    def "configuration cache without any Java agent succeeds without problem [#mode]"() {
        when:
        def output = testRunner(mode).withArguments("--configuration-cache").build().output

        then:
        !output.contains("Configuration cache problems found")

        where:
        mode << PluginResolutionMode.values()
    }

    @Issue("https://github.com/gradle/gradle/issues/25929")
    def "third-party Java agent without a transformer does not cause a configuration-cache problem [#mode]"() {
        given:
        def agentJar = buildStubAgentJar()

        when:
        def output = testRunner(mode)
            .withJvmArguments("-javaagent:${agentJar}")
            .withArguments("--configuration-cache")
            .build()
            .output

        then:
        !output.contains(JAVA_AGENT_PROBLEM_MESSAGE)
        !output.contains("Configuration cache problems found")

        where:
        mode << PluginResolutionMode.values()
    }

    def "third-party Java agent that registers a class-file transformer does not cause a configuration-cache problem [#mode]"() {
        given:
        def agentJar = buildTransformerAgentJar()

        when:
        def output = testRunner(mode)
            .withJvmArguments("-javaagent:${agentJar}")
            .withArguments("--configuration-cache")
            .build()
            .output

        then:
        !output.contains(JAVA_AGENT_PROBLEM_MESSAGE)
        !output.contains("Configuration cache problems found")

        where:
        mode << PluginResolutionMode.values()
    }

    def "JDWP debug agent does not cause a configuration-cache problem [#mode]"() {
        // JDWP attaches via `-agentlib:`, which the detection flags conservatively as a third-party agent
        // (any JVMTI agent can subscribe to ClassFileLoadHook and transform bytecode). JDWP itself does
        // not register a transformer, so the build proceeds cleanly via the compose path.
        when:
        def output = testRunner(mode)
            .withJvmArguments("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:0")
            .withArguments("--configuration-cache")
            .build()
            .output

        then:
        !output.contains("Configuration cache problems found")

        where:
        mode << PluginResolutionMode.values()
    }

    def "native JVMTI agent attached via -agentpath: does not cause a configuration-cache problem [#mode]"() {
        // Use the JDK-shipped JDWP shared library so we exercise the -agentpath: codepath without
        // requiring a custom native artifact in the test fixtures.
        given:
        def jdwpLibrary = jdwpAgentLibraryPath()

        when:
        def output = testRunner(mode)
            .withJvmArguments("-agentpath:${jdwpLibrary}=transport=dt_socket,server=y,suspend=n,address=*:0")
            .withArguments("--configuration-cache")
            .build()
            .output

        then:
        !output.contains("Configuration cache problems found")

        where:
        mode << PluginResolutionMode.values()
    }

    def "buildscript classpath project dependencies load under the compose path with a third-party agent [#mode]"() {
        given:
        def agentJar = buildTransformerAgentJar()
        file("buildSrc/src/main/java/test/Helper.java") << """
            package test;
            public class Helper {
                public static String value() {
                    return "from-build-src";
                }
            }
        """
        buildFile << """
            println("helper says " + test.Helper.value())
            tasks.register("noop")
        """

        when:
        def output = testRunner(mode)
            .withJvmArguments("-javaagent:${agentJar}")
            .withArguments("--configuration-cache", "noop")
            .build()
            .output

        then:
        !output.contains("Configuration cache problems found")
        output.contains("helper says from-build-src")

        where:
        mode << PluginResolutionMode.values()
    }

    def "third-party Java agent that writes a file from premain runs under CC [#mode]"() {
        given:
        def agentJar = buildFileWritingAgentJar()
        def destFile = file("agent-output.txt")
        buildFile << """
            tasks.register("noop")
        """

        when:
        def output = testRunner(mode)
            .withJvmArguments("-javaagent:${agentJar}=destfile=${destFile.absolutePath}")
            .withArguments("--configuration-cache", "noop")
            .build()
            .output

        then:
        !output.contains("Configuration cache problems found")
        destFile.exists()
        destFile.length() > 0

        where:
        mode << PluginResolutionMode.values()
    }

    // The CC problem is only emitted from InjectedClasspathInstrumentationStrategy, which is invoked
    // when TestKit's injected classpath registers DefaultInjectedClasspathPluginResolver. The same
    // unsafe condition (Gradle agent off + third-party agent present) also affects non-injected
    // buildscript classpaths, but no problem is reported there today. Centralizing the report so it
    // fires for both modes is tracked as a follow-up.
    def "third-party Java agent with Gradle's instrumentation agent disabled is reported as a CC problem [INJECTED_CLASSPATH]"() {
        given:
        def agentJar = buildStubAgentJar()

        when:
        def output = testRunner(PluginResolutionMode.INJECTED_CLASSPATH)
            .withJvmArguments("-javaagent:${agentJar}")
            .withArguments("--configuration-cache", "-Dorg.gradle.internal.instrumentation.agent=false")
            .buildAndFail()
            .output

        then:
        output.contains(JAVA_AGENT_PROBLEM_MESSAGE)
    }

    def "third-party Java agent with Gradle's instrumentation agent disabled without CC succeeds [#mode]"() {
        given:
        def agentJar = buildStubAgentJar()

        when:
        def output = testRunner(mode)
            .withJvmArguments("-javaagent:${agentJar}")
            .withArguments("-Dorg.gradle.internal.instrumentation.agent=false")
            .build()
            .output

        then:
        !output.contains(JAVA_AGENT_PROBLEM_MESSAGE)
        !output.contains("Configuration cache problems found")

        where:
        mode << PluginResolutionMode.values()
    }

    @Issue("https://github.com/gradle/gradle/issues/27956")
    @Requires(
        value = TestExecutionPreconditions.NotEmbeddedExecutor,
        reason = "Uses TestKit withDebug(true), so everything run in-process, masking classloader-sensitive behavior"
    )
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

    @Issue("https://github.com/gradle/gradle/issues/27956")
    @Requires(
        value = TestExecutionPreconditions.NotEmbeddedExecutor,
        reason = "Uses TestKit withDebug(true), so everything run in-process, masking classloader-sensitive behavior"
    )
    def "dependencies of builds tested with TestKit in debug mode and resolved from a Maven repo are instrumented and violations are reported"() {
        given:
        def localRepoPath = TextUtil.normaliseFileSeparators(file("local-repo").absolutePath)
        file("included/src/main/java/test/gradle/MyPlugin.java") << """
            package test.gradle;
            import org.gradle.api.*;

            public class MyPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    String returned = System.getProperty("my.property");
                    System.out.println("returned = " + returned);
                }
            }
        """
        file("included/build.gradle") << """
            plugins {
                id 'java-gradle-plugin'
                id 'maven-publish'
            }
            group = "test.gradle"
            version = "1.0.0"
            gradlePlugin {
                plugins {
                    create("my-plugin") {
                        id = "test.my-plugin"
                        implementationClass = "test.gradle.MyPlugin"
                    }
                }
            }
            publishing {
                repositories {
                    maven {
                        name = "localRepo"
                        url = uri("$localRepoPath")
                    }
                }
            }
        """
        file("included/settings.gradle") << "rootProject.name = 'included'"
        settingsFile.text = """
            pluginManagement {
                repositories {
                    maven { url = uri("$localRepoPath") }
                }
            }
            rootProject.name = 'root'
        """
        buildFile.text = """
            plugins {
                id 'test.my-plugin' version '1.0.0'
            }
        """

        when:
        executer.inDirectory(file("included")).withTasks("publishAllPublicationsToLocalRepoRepository").run()
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
            withInput("Plugin 'test.my-plugin': system property 'my.property'")
            ignoringUnexpectedInputs()
        }
        output.contains("Configuration cache entry stored.")
    }

    /**
     * This test check that Jacoco works with TestKit when configuration cache is DISABLED.
     *
     * We broke --no-configuration-cache case already twice in the past, so it's worth testing it.
     */
    @Issue(["https://github.com/gradle/gradle/issues/13614", "https://github.com/gradle/gradle/issues/28729"])
    @Requires(
        value = TestExecutionPreconditions.NotEmbeddedExecutor,
        reason = "The :test JVM is forked with the Jacoco agent attached"
    )
    def "running a test that applies Jacoco with TestKit should generate a test report when running without configuration cache [#mode]"() {
        given:
        setUpJacocoTestKitProject("--no-configuration-cache", mode)

        when:
        succeeds("jacocoTestCoverageVerification")

        then:
        def report = new JacocoReportXmlFixture(file("build/reports/jacoco/test/jacocoTestReport.xml"))
        report.assertHasClassCoverage("test.gradle.MyPlugin")
        report.assertMethodHasLineCoverage("test.gradle.MyPlugin", "apply")

        where:
        mode << PluginResolutionMode.values()
    }

    @Issue("https://github.com/gradle/gradle/issues/25979")
    @Requires(
        value = TestExecutionPreconditions.NotEmbeddedExecutor,
        reason = "The :test JVM is forked with the Jacoco agent attached"
    )
    def "running a test that applies Jacoco with TestKit should generate a test report when running with configuration cache [#mode]"() {
        given:
        setUpJacocoTestKitProject("--configuration-cache", mode)

        when:
        succeeds("jacocoTestCoverageVerification")

        then:
        def report = new JacocoReportXmlFixture(file("build/reports/jacoco/test/jacocoTestReport.xml"))
        report.assertHasClassCoverage("test.gradle.MyPlugin")
        report.assertMethodHasLineCoverage("test.gradle.MyPlugin", "apply")

        where:
        mode << PluginResolutionMode.values()
    }

    private static String jdwpAgentLibraryPath() {
        def javaHome = new File(System.getProperty("java.home"))
        def libDir = new File(javaHome, "lib")
        def candidates = ["libjdwp.dylib", "libjdwp.so"]
        for (name in candidates) {
            def candidate = new File(libDir, name)
            if (candidate.exists()) {
                return TextUtil.normaliseFileSeparators(candidate.absolutePath)
            }
        }
        throw new IllegalStateException("Could not locate libjdwp under ${libDir}")
    }

    private GradleRunner testRunner(PluginResolutionMode mode) {
        // Ensure the project boundary so Gradle doesn't walk up the filesystem looking for one.
        settingsFile.touch()
        def runner = GradleRunner.create()
            .withGradleInstallation(buildContext.gradleHomeDir)
            .forwardOutput()
            .withProjectDir(testDirectory)
        if (mode == PluginResolutionMode.INJECTED_CLASSPATH) {
            runner = runner.withPluginClasspath([new File("some-dir")])
        }
        runner
    }

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

    private void setUpJacocoTestKitProject(String innerCcArgument, PluginResolutionMode mode) {
        def jacocoDestinationFile = TextUtil.normaliseFileSeparators("${jacocoDestinationDir.absolutePath}/jacoco.exec")
        def localRepoPath = TextUtil.normaliseFileSeparators(file("local-repo").absolutePath)
        def isMavenRepo = mode == PluginResolutionMode.MAVEN_REPO

        buildFile << """
            plugins {
                id 'java'
                id 'groovy'
                id 'jacoco'
                id 'java-gradle-plugin'
                ${isMavenRepo ? "id 'maven-publish'" : ""}
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

            ${isMavenRepo ? '''
                group = "test.gradle"
                version = "1.0.0"
            ''' : ''}

            gradlePlugin {
                plugins {
                    create("my-plugin") {
                        id = "test.my-plugin"
                        implementationClass = "test.gradle.MyPlugin"
                        ${isMavenRepo ? "" : 'version = "1.0.0"'}
                    }
                }
            }

            ${isMavenRepo ? """
                publishing {
                    repositories {
                        maven {
                            name = "localRepo"
                            url = uri("$localRepoPath")
                        }
                    }
                }
            """ : ""}

            test {
                test.extensions.getByType(JacocoTaskExtension).destinationFile = new File("$jacocoDestinationFile")
                systemProperty "jacocoAgentJar", configurations.jacocoRuntime.singleFile.absolutePath
                systemProperty "jacocoDestFile", test.extensions.getByType(JacocoTaskExtension).destinationFile.absolutePath
                ${isMavenRepo ? """
                    systemProperty "localRepoPath", "$localRepoPath"
                    dependsOn(tasks.named("publishAllPublicationsToLocalRepoRepository"))
                """ : ""}
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

        def innerPluginsBlock = isMavenRepo
            ? "plugins { id('test.my-plugin') version '1.0.0' }"
            : "plugins { id('test.my-plugin') }"
        def innerSettingsSetup = isMavenRepo
            ? """
                settingsFile = new File(testProjectDir, 'settings.gradle')
                settingsFile << '''
                    pluginManagement {
                        repositories {
                            maven { url = uri("''' + System.getProperty('localRepoPath') + '''") }
                        }
                    }
                    rootProject.name = 'test'
                '''
            """
            : """
                new File(testProjectDir, 'settings.gradle') << "rootProject.name = 'test'"
            """
        def innerRunnerExtras = isMavenRepo ? "" : ".withPluginClasspath()"

        file("src/test/groovy/Test.groovy") << """
            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import spock.lang.Specification
            import spock.lang.TempDir

            class Test extends Specification {
                @TempDir
                File testProjectDir
                ${isMavenRepo ? "File settingsFile" : ""}
                File buildFile
                File gradleProperties

                def setup() {
                    ${innerSettingsSetup}
                    buildFile = new File(testProjectDir, 'build.gradle')
                    gradleProperties = new File(testProjectDir, 'gradle.properties')
                }

                def "run Gradle build with Jacoco"() {
                    given:
                    def jacocoAgentJar = System.getProperty("jacocoAgentJar")
                    def jacocoDestFile = System.getProperty("jacocoDestFile")
                    buildFile << "${innerPluginsBlock}"
                    gradleProperties << "org.gradle.jvmargs=\\"-javaagent:\$jacocoAgentJar=destfile=\$jacocoDestFile\\""

                    when:
                    def result = GradleRunner.create()
                        .withProjectDir(testProjectDir)
                        .withArguments("testTask", "$innerCcArgument")
                        ${innerRunnerExtras}
                        .withDebug(false)
                        .forwardOutput()
                        .build()

                    then:
                    noExceptionThrown()
                }
            }
        """
    }
}
