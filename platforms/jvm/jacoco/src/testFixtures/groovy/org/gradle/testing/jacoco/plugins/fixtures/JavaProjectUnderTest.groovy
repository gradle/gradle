/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testing.jacoco.plugins.fixtures

import org.gradle.test.fixtures.file.TestFile

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepository

class JavaProjectUnderTest {
    private final TestFile projectDir
    private final TestFile buildFile

    JavaProjectUnderTest(TestFile projectDir) {
        this.projectDir = projectDir
        buildFile = projectDir.file('build.gradle')
    }

    private TestFile file(Object... path) {
        projectDir.file(path)
    }

    JavaProjectUnderTest writeBuildScript() {
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'jacoco'

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13'
            }
        """
        this
    }

    JavaProjectUnderTest writeSourceFiles(int count = 1) {
        writeProductionSourceFile(count)
        writeTestSourceFile('src/test/java', count)
        this
    }

    JavaProjectUnderTest writeIntegrationTestSourceFiles(int count = 1) {
        String testSrcDir = 'src/integTest/java'
        buildFile << """
            sourceSets {
                integrationTest {
                    java {
                        srcDir file('$testSrcDir')
                    }
                    compileClasspath += sourceSets.main.output + configurations.testRuntimeClasspath
                    runtimeClasspath += output + compileClasspath
                }
            }

            task integrationTest(type: Test) {
                testClassesDirs = sourceSets.integrationTest.output.classesDirs
                classpath = sourceSets.integrationTest.runtimeClasspath
            }

            task jacocoIntegrationTestReport(type: JacocoReport) {
                executionData integrationTest
                sourceSets sourceSets.main
            }

            task jacocoIntegrationTestCoverageVerification(type: JacocoCoverageVerification) {
                executionData integrationTest
                sourceSets sourceSets.main
            }
        """

        writeTestSourceFile(testSrcDir, count, "IntegrationTest")
        this
    }

    private void writeProductionSourceFile(int count = 1) {
        (1..count).each { index ->
            file("src/main/java/org/gradle/Class${index}.java").text = """
            package org.gradle;

            public class Class${index} {
                public boolean isFoo(Object arg) {
                    return true;
                }
            }
        """
        }
    }

    private void writeTestSourceFile(String baseDir, int count = 1, String type="Test") {
        (1..count).each { index ->
            file("$baseDir/org/gradle/Class${index}${type}.java").text = """
            package org.gradle;

            import org.junit.Test;

            public class Class${index}${type} {
                @Test
                public void someTest() {
                    new Class${index}().isFoo("test");
                }
            }
        """
        }
    }
}
