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

import org.gradle.integtests.fixtures.build.BuildTestFile

class CompositeBuildFailureCollectionIntegrationTest extends AbstractCompositeBuildIntegrationTest {

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

        includedBuilds << buildB << buildC << buildD

        buildA.buildFile << """
            test.dependsOn gradle.includedBuilds*.task(':test')
        """
    }

    def "can collect all build failures"() {
        when:
        fails(buildA, 'test', ['--continue'])

        then:
        assertTaskExecuted(":buildB", ":test")
        assertTaskExecuted(":buildC", ":sub1:test")
        assertTaskExecuted(":buildC", ":sub2:test")
        assertTaskExecuted(":buildC", ":sub3:test")
        assertTaskExecuted(":buildD", ":test")
        errorOutput.contains('Multiple build failures')
        errorOutput.contains("Execution failed for task ':buildB:test'")
        errorOutput.contains("Execution failed for task ':buildC:sub1:test'")
        errorOutput.contains("Execution failed for task ':buildC:sub2:test'")
        errorOutput.contains("Execution failed for task ':buildC:sub3:test'")
        errorOutput.contains("Execution failed for task ':buildD:test'")
    }

    private String javaProject() {
        """
            apply plugin: 'java'

            ${mavenCentralRepository()}
            
            dependencies {
                testCompile 'junit:junit:4.12'
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
