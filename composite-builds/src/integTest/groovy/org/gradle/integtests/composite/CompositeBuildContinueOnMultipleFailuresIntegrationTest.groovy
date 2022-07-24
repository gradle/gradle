/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.composite

import org.gradle.initialization.StartParameterBuildOptions.ContinueOption
import org.gradle.integtests.fixtures.build.BuildTestFile

/**
 * Tests for composite build delegating to tasks in an included build that produce more than one failure.
 */
class CompositeBuildContinueOnMultipleFailuresIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    private static final String CONTINUE_COMMAND_LINE_OPTION = "--$ContinueOption.LONG_OPTION"
    BuildTestFile buildB
    BuildTestFile buildC
    BuildTestFile buildD

    def setup() {
        buildB = singleProjectBuild('buildB') {
            buildFile << javaProject()
            file('src/test/java/SampleTestB.java') << junitTestClass('SampleTestB')
        }

        buildC = multiProjectBuild('buildC', ['sub1', 'sub2', 'sub3']) {
            buildFile << """
                allprojects {
                    ${javaProject()}
                }

                test.dependsOn 'sub1:test', 'sub2:test', 'sub3:test'
            """
            file('sub1/src/test/java/SampleTestC_Sub1.java') << junitTestClass('SampleTestC_Sub1')
            file('sub2/src/test/java/SampleTestC_Sub2.java') << junitTestClass('SampleTestC_Sub2')
            file('sub3/src/test/java/SampleTestC_Sub3.java') << junitTestClass('SampleTestC_Sub3')
        }

        buildD = singleProjectBuild('buildD') {
            buildFile << javaProject()
            file('src/test/java/SampleTestD.java') << junitTestClass('SampleTestD')
        }
    }

    def "can collect build failures from multiple included builds"() {
        when:
        includedBuilds << buildB << buildC << buildD

        buildA.buildFile << """
            task testAll {
                dependsOn gradle.includedBuilds*.task(':test')
            }
        """

        and:
        fails(buildA, 'testAll', [CONTINUE_COMMAND_LINE_OPTION])

        then:
        assertTaskExecuted(':buildB', ':test')
        assertTaskExecuted(':buildC', ':sub1:test')
        assertTaskExecuted(':buildC', ':sub2:test')
        assertTaskExecuted(':buildC', ':sub3:test')
        assertTaskExecuted(':buildD', ':test')
        failure.assertHasFailures(5)
        failure.assertHasDescription("Execution failed for task ':buildB:test'.")
        failure.assertHasDescription("Execution failed for task ':buildC:sub1:test'.")
        failure.assertHasDescription("Execution failed for task ':buildC:sub2:test'.")
        failure.assertHasDescription("Execution failed for task ':buildC:sub3:test'.")
        failure.assertHasDescription("Execution failed for task ':buildD:test'.")
    }

    def "can collect build failure in root and included build"() {
        when:
        includedBuilds << buildC

        buildA.buildFile << """
            ${mavenCentralRepository()}
            ${junitDependency()}

            task testAll {
                dependsOn 'test'
                dependsOn gradle.includedBuilds*.task(':test')
            }
        """
        file('buildA/src/test/java/SampleTestA.java') << junitTestClass('SampleTestA')

        and:
        fails(buildA, 'testAll', [CONTINUE_COMMAND_LINE_OPTION])

        then:
        assertTaskExecuted(':', ':test')
        assertTaskExecuted(':buildC', ':sub1:test')
        assertTaskExecuted(':buildC', ':sub2:test')
        assertTaskExecuted(':buildC', ':sub3:test')
        failure.assertHasFailures(4)
        failure.assertHasDescription("Execution failed for task ':test'.")
        failure.assertHasDescription("Execution failed for task ':buildC:sub1:test'.")
        failure.assertHasDescription("Execution failed for task ':buildC:sub2:test'.")
        failure.assertHasDescription("Execution failed for task ':buildC:sub3:test'.")
    }

    private String javaProject() {
        """
            apply plugin: 'java'

            ${mavenCentralRepository()}
            ${junitDependency()}
        """
    }

    static String junitDependency() {
        """
            dependencies {
                testImplementation 'junit:junit:4.13'
            }
        """
    }

    static String junitTestClass(String className) {
        """
            import org.junit.Test;
            import static org.junit.Assert.assertTrue;

            public class $className {
                @Test
                public void testSuccess() {
                    assertTrue(true);
                }

                @Test
                public void testFailure() {
                    throw new RuntimeException("Failure!");
                }
            }
        """
    }

}
