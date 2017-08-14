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

package org.gradle.internal.logging.console.jvm

import org.gradle.integtests.fixtures.AbstractConsoleFunctionalSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Unroll

@IgnoreIf({ GradleContextualExecuter.isParallel() })
class ConsoleJvmTestWorkerFunctionalTest extends AbstractConsoleFunctionalSpec {

    private static final int MAX_WORKERS = 2
    private static final String SERVER_RESOURCE_1 = 'test-1'
    private static final String SERVER_RESOURCE_2 = 'test-2'

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        executer.withArguments('--parallel', "--max-workers=$MAX_WORKERS")
        server.start()
    }

    @Unroll
    def "shows test class execution #description test class name in work-in-progress area of console for single project build"() {
        given:
        buildFile << testableJavaProject()
        file("src/test/java/${testClass1.fileRepresentation}") << junitTest(testClass1.classNameWithoutPackage, SERVER_RESOURCE_1)
        file("src/test/java/${testClass2.fileRepresentation}") << junitTest(testClass2.classNameWithoutPackage, SERVER_RESOURCE_2)
        def testExecution = server.expectConcurrentAndBlock(2, SERVER_RESOURCE_1, SERVER_RESOURCE_2)

        when:
        def gradleHandle = executer.withTasks('test').start()
        testExecution.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            assert containsTestExecutionWorkInProgressLine(gradleHandle, ':test', testClass1.renderedClassName)
            assert containsTestExecutionWorkInProgressLine(gradleHandle, ':test', testClass2.renderedClassName)
        }

        testExecution.release(2)
        gradleHandle.waitForFinish()

        where:
        testClass1                    | testClass2                    | description
        JavaTestClass.PRESERVED_TEST1 | JavaTestClass.PRESERVED_TEST2 | 'preserved'
        JavaTestClass.SHORTENED_TEST1 | JavaTestClass.SHORTENED_TEST2 | 'shortened'
    }

    @Unroll
    def "shows test class execution #description test class name in work-in-progress area of console for multi-project build"() {
        given:
        settingsFile << "include 'project1', 'project2'"
        buildFile << """
            subprojects {
                ${testableJavaProject()}
            }
        """
        file("project1/src/test/java/${testClass1.fileRepresentation}") << junitTest(testClass1.classNameWithoutPackage, SERVER_RESOURCE_1)
        file("project2/src/test/java/${testClass2.fileRepresentation}") << junitTest(testClass2.classNameWithoutPackage, SERVER_RESOURCE_2)
        def testExecution = server.expectConcurrentAndBlock(2, SERVER_RESOURCE_1, SERVER_RESOURCE_2)

        when:
        def gradleHandle = executer.withTasks('test').start()
        testExecution.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            assert containsTestExecutionWorkInProgressLine(gradleHandle, ':project1:test', testClass1.renderedClassName)
            assert containsTestExecutionWorkInProgressLine(gradleHandle, ':project2:test', testClass2.renderedClassName)
        }

        testExecution.release(2)
        gradleHandle.waitForFinish()

        where:
        testClass1                    | testClass2                    | description
        JavaTestClass.PRESERVED_TEST1 | JavaTestClass.PRESERVED_TEST2 | 'preserved'
        JavaTestClass.SHORTENED_TEST1 | JavaTestClass.SHORTENED_TEST2 | 'shortened'
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
                jcenter()
            }
            
            dependencies {
                testCompile 'junit:junit:4.12'
            }
            
            tasks.withType(Test) {
                maxParallelForks = $MAX_WORKERS
            }
        """
    }

    static boolean containsTestExecutionWorkInProgressLine(GradleHandle gradleHandle, String taskPath, String testName) {
        gradleHandle.standardOutput.contains(workInProgressLine("> $taskPath > Executing test $testName"))
    }

    private static class JavaTestClass {
        public static final PRESERVED_TEST1 = new JavaTestClass('org.gradle.Test1', 'org.gradle.Test1')
        public static final PRESERVED_TEST2 = new JavaTestClass('org.gradle.Test2', 'org.gradle.Test2')
        public static final SHORTENED_TEST1 = new JavaTestClass('org.gradle.AdvancedJavaPackageAbbreviatingClassFunctionalTest', 'org...AdvancedJavaPackageAbbreviatingClassFunctionalTest')
        public static final SHORTENED_TEST2 = new JavaTestClass('org.gradle.EvenMoreAdvancedJavaPackageAbbreviatingJavaClassFunctionalTest', '...EvenMoreAdvancedJavaPackageAbbreviatingJavaClassFunctionalTest')

        private final String fullyQualifiedClassName
        private final String renderedClassName

        JavaTestClass(String fullyQualifiedClassName, String renderedClassName) {
            this.fullyQualifiedClassName = fullyQualifiedClassName
            this.renderedClassName = renderedClassName
        }

        String getFileRepresentation() {
            fullyQualifiedClassName.replace('.', '/') + '.java'
        }

        String getClassNameWithoutPackage() {
            fullyQualifiedClassName.substring(fullyQualifiedClassName.lastIndexOf('.') + 1, fullyQualifiedClassName.length())
        }

        String getRenderedClassName() {
            renderedClassName
        }
    }
}
