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

    JavaProjectUnderTest writeOfflineInstrumentation(boolean enabled, String task = "test") {
        buildFile << """
            ${task} {
                jacoco {
                    offline.set(${enabled})
                }
            }
        """
        this
    }

    JavaProjectUnderTest writeSourceFiles(int count = 1, String sourceSet = "main", String classSuffix = "") {
        writeProductionSourceFile(count, sourceSet, classSuffix)
        writeTestSourceFile(count, "test", classSuffix + "Test", classSuffix)
        this
    }

    JavaProjectUnderTest writeIntegrationTestSourceFiles(int count = 1) {
        buildFile << """
            sourceSets {
                integrationTest {
                    java {
                        srcDir file('src/integTest/java')
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

        writeTestSourceFile(count, "integTest", "IntegrationTest", "")
        this
    }

    private void writeProductionSourceFile(int count, String sourceSet, String classSuffix) {
        (1..count).each { index ->
            file("src/${sourceSet}/java/org/gradle/Class${index}${classSuffix}.java").text = """
            package org.gradle;

            public class Class${index}${classSuffix} {
                public boolean isFoo(Object arg) {
                    return true;
                }
            }
        """
        }
    }

    private void writeTestSourceFile(int count, String testSourceSet, String testClassSuffix, String prodClassSuffix) {
        (1..count).each { index ->
            file("src/${testSourceSet}/java/org/gradle/Class${index}${testClassSuffix}.java").text = """
            package org.gradle;

            import org.junit.Test;

            public class Class${index}${testClassSuffix} {
                @Test
                public void someTest() {
                    new Class${index}${prodClassSuffix}().isFoo("test");
                }
            }
        """
        }
    }
}
