/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.tooling.r85

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import spock.lang.Issue

@TargetGradleVersion('>=8.5')
class TestLauncherDebugCrossVersionTest extends ToolingApiSpecification {

    @Issue('https://github.com/gradle/gradle/issues/26366')
    def "Run test twice (#scenarioName)"() {
        setup:
        sampleBuildWithTest()

        when:
        String output1 = runTaskAndTestClassUsing(firstDebug)
        String output2 = runTaskAndTestClassUsing(secondDebug)

        then:
        assertTestDebugMode(output1, firstDebug)
        assertTestDebugMode(output2, secondDebug)

        where:
        scenarioName                     | firstDebug | secondDebug
        "first wo/debug, second w/debug" | false      | true
        "first w/debug, second wo/debug" | true       | false
    }

    private def sampleBuildWithTest() {
        settingsFile << "include('app')"
        javaBuildWithTests(file('app'))
    }

    int retryCounter = 0

    private String runTaskAndTestClassUsing(boolean debugMode) {
        def stdout = new ByteArrayOutputStream()
        withConnection { ProjectConnection connection ->
            def testLauncher = connection.newTestLauncher()
                .withTaskAndTestClasses(':app:test', Arrays.asList('TestClass1'))
                .setStandardOutput(stdout)
            if (debugMode) {
                // JDWPUtil is flaky https://github.com/gradle/gradle/issues/26366
                testLauncher.debugTestsOn(49052 + (retryCounter++))
            }
            testLauncher.run(resultHandlerFor(debugMode))
        }
        stdout.toString('utf-8')
    }

    private resultHandlerFor(final boolean debugMode) {
        new ResultHandler<Void>() {

            @Override
            void onComplete(Void result) {
                if (debugMode) {
                    throw new Exception("Test task running in non-debug mode should pass")
                }
            }

            @Override
            void onFailure(GradleConnectionException failure) {
                if (!debugMode) {
                    throw new Exception("Test running in debug mode should fail if no debugger is present")
                }
            }
        }
    }

    private void javaBuildWithTests(TestFile projectDir) {
        propertiesFile << 'org.gradle.configuration-cache=true'
        projectDir.file('settings.gradle') << ''
        javaLibraryWithTests(projectDir)
    }

    private void javaLibraryWithTests(TestFile projectDir) {
        projectDir.file('build.gradle') << '''
            plugins {
                id 'java-library'
            }
            repositories {
                mavenCentral()
            }
            testing {
                suites {
                    test {
                        useJUnitJupiter()
                    }
                }
            }
            tasks.named('test') {
                testLogging {
                    showStandardStreams = true
                }
                doFirst {
                    System.out.println("Debug mode enabled: " + debugOptions.enabled.get())
                }
            }
        '''
        writeTestClass(projectDir, 'TestClass1')
        writeTestClass(projectDir, 'TestClass2')
    }

    private TestFile writeTestClass(TestFile projectDir, String testClassName) {
        projectDir.file("src/test/java/${testClassName}.java") << """
            public class $testClassName {
                @org.junit.jupiter.api.Test
                void testMethod() {
                    System.out.println("${testClassName}.testMethod");
                }
            }
        """
    }

    private void assertTestDebugMode(String output, boolean debugMode) {
        assert output.contains("Debug mode enabled: $debugMode")
    }
}
