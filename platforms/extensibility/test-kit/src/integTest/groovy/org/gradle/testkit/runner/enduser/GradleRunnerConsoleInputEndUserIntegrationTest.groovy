/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.testkit.runner.enduser

import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.TextUtil

import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.DUMMY_TASK_NAME
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.PROMPT
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.answerOutput
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.buildScanPlugin
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.buildScanPluginApplication

@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_EMBEDDED_REASON)
class GradleRunnerConsoleInputEndUserIntegrationTest extends BaseTestKitEndUserIntegrationTest {

    def setup() {
        buildFile << """
            import org.gradle.internal.jvm.JpmsConfiguration

            apply plugin: 'groovy'

            dependencies {
                testImplementation localGroovy()
                testImplementation gradleTestKit()
            }

            testing {
                suites {
                    test {
                        targets {
                            all {
                                testTask.configure {
                                    if (JavaVersion.current().isJava9Compatible()) {
                                        // Normally, test runners are not inheriting the JVM arguments from the Gradle daemon.
                                        // This case though, we need it, as we are executing a compilation task inside the nested build.
                                        jvmArgs = JpmsConfiguration.GRADLE_DAEMON_JPMS_ARGS
                                    }
                                }
                            }
                        }
                        useSpock()
                    }
                }
            }

            ${mavenCentralRepository()}
        """
    }

    def "can capture user input if standard input was provided"() {
        when:
        file("src/test/groovy/Test.groovy") << functionalTest("""
            // defer sending the answer until prompted
            def stdin = new PipedInputStream()
            def stdinSink = new PipedOutputStream(stdin)
            def stdout = new java.io.Writer() {
                def buffer = new StringBuilder()
                def written = false

                void write(char[] cbuf, int off, int len) {
                    buffer.append(cbuf, off, len)
                    if (!written && buffer.toString().contains('$PROMPT')) {
                        written = true
                        stdinSink.write(\"\"\"yes${TextUtil.platformLineSeparator}\"\"\".bytes)
                        stdinSink.close()
                    }
                }

                void flush() { }

                void close() { }
            }

            when:
            def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments('$DUMMY_TASK_NAME')
                .withDebug($debug)
                .withStandardInput(stdin)
                .forwardStdOutput(stdout)
                .build()

            then:
            result.output.contains('$PROMPT')
            result.output.contains('${answerOutput(true)}')
        """)

        then:
        succeeds 'build', '-i'
        executedAndNotSkipped ':test'
        new JUnitXmlTestExecutionResult(projectDir).totalNumberOfTestClassesExecuted > 0
    }

    def "cannot capture user input if standard in was not provided"() {
        when:
        file("src/test/groovy/Test.groovy") << functionalTest("""
            when:
            def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments('$DUMMY_TASK_NAME')
                .withDebug($debug)
                .build()

            then:
            !result.output.contains('$PROMPT')
            result.output.contains('${answerOutput(null)}')
        """)

        then:
        succeeds 'build'
        executedAndNotSkipped ':test'
        new JUnitXmlTestExecutionResult(projectDir).totalNumberOfTestClassesExecuted > 0
    }

    static String functionalTest(String content) {
        """
            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import spock.lang.Specification
            import spock.lang.TempDir

            class Test extends Specification {
                @TempDir File testProjectDir
                File buildFile

                def setup() {
                    def buildSrcDir = new File(testProjectDir, 'buildSrc/src/main/java').tap { mkdirs() }
                    def pluginFile = new File(buildSrcDir, 'BuildScanPlugin.java')
                    pluginFile << '''${buildScanPlugin()}'''
                    def settingsFile = new File(testProjectDir, 'settings.gradle')
                    settingsFile << "rootProject.name = 'test'"
                    buildFile = new File(testProjectDir, 'build.gradle')
                    buildFile << '''${buildScanPluginApplication()}'''
                }

                def "capture user input"() {
                    $content
                }
            }
        """
    }
}
