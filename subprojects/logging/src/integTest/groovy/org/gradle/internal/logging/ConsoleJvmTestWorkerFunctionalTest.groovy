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

package org.gradle.internal.logging

import org.gradle.integtests.fixtures.AbstractConsoleFunctionalSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.IgnoreIf

class ConsoleJvmTestWorkerFunctionalTest extends AbstractConsoleFunctionalSpec {

    private static final String SERVER_RESOURCE_1 = 'test-1'
    private static final String SERVER_RESOURCE_2 = 'test-2'

    @Rule
    BlockingHttpServer server = new BlockingHttpServer(40000)

    def setup() {
        server.start()
    }

    def "shows test class execution in work-in-progress area of console for single project build"() {
        given:
        buildFile << testableJavaProject()
        file('src/test/java/org/gradle/Test1.java') << junitTest('Test1', SERVER_RESOURCE_1)
        file('src/test/java/org/gradle/Test2.java') << junitTest('Test2', SERVER_RESOURCE_2)
        def testExecution = server.expectConcurrentAndBlock(SERVER_RESOURCE_1, SERVER_RESOURCE_2)

        when:
        def gradleHandle = executer.withTasks('test').start()
        testExecution.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            assert matchesOutput(gradleHandle.standardOutput, ".*> :test > Executing test org\\.gradle\\.Test1.*")
            assert matchesOutput(gradleHandle.standardOutput, ".*> :test > Executing test org\\.gradle\\.Test2.*")
        }

        testExecution.releaseAll()
        gradleHandle.waitForFinish()
    }

    @IgnoreIf({ GradleContextualExecuter.isParallel() })
    def "shows test class execution in work-in-progress area of console for multi-project build"() {
        given:
        settingsFile << "include 'project1', 'project2'"
        buildFile << """
            subprojects {
                ${testableJavaProject()}
            }
        """
        file('project1/src/test/java/org/gradle/Test1.java') << junitTest('Test1', SERVER_RESOURCE_1)
        file('project2/src/test/java/org/gradle/Test2.java') << junitTest('Test2', SERVER_RESOURCE_2)
        def testExecution = server.expectConcurrentAndBlock(SERVER_RESOURCE_1, SERVER_RESOURCE_2)

        when:
        def gradleHandle = executer.withArguments('--parallel', '--max-workers=4').withTasks('test').start()
        testExecution.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            assert matchesOutput(gradleHandle.standardOutput, ".*> :project1:test > Executing test org\\.gradle\\.Test1.*")
            assert matchesOutput(gradleHandle.standardOutput, ".*> :project2:test > Executing test org\\.gradle\\.Test2.*")
        }

        testExecution.releaseAll()
        gradleHandle.waitForFinish()
    }

    private String junitTest(String testClassName, String serverResource) {
        """
            package org.gradle;

            import org.junit.Test;

            public class $testClassName {
                @Test
                public void longRunningTest() {
                    ${server.callFromBuild(serverResource)}
                }
            }
        """
    }

    static String testableJavaProject() {
        """
            apply plugin: 'java'
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                testCompile 'junit:junit:4.12'
            }
            
            tasks.withType(Test) {
                maxParallelForks = 3
            }
        """
    }

    static boolean matchesOutput(String output, String regexToFind) {
        (output =~ /(?ms)($regexToFind)/).matches()
    }
}
