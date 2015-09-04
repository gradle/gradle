/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult

class DependencyHandlerApiResolveIntegrationTest extends AbstractIntegrationSpec {
    public static final String GRADLE_TEST_KIT_JAR_BASE_NAME = 'gradle-test-kit-'

    def setup() {
        executer.requireGradleHome()

        buildFile << """
            apply plugin: 'java'

            task resolveLibs(type: Copy) {
                ext.extractedDir = file('\$buildDir/libs')
                from configurations.testCompile
                into extractedDir
            }

            task verifyTestKitJars {
                dependsOn resolveLibs
            }
        """

        file('src/test/java/com/gradle/example/MyTest.java') << javaClassReferencingTestKit()
    }

    def "gradleTestKit dependency API adds test-kit classes and can compile against them"() {
        given:
        buildFile << testKitDependency()
        buildFile << """
            verifyTestKitJars {
                doLast {
                    def jarFiles = resolveLibs.extractedDir.listFiles()
                    def testKitFunctionalJar = jarFiles.find { it.name.startsWith('$GRADLE_TEST_KIT_JAR_BASE_NAME') }
                    assert testKitFunctionalJar

                    def jar = new java.util.jar.JarFile(testKitFunctionalJar)
                    def jarFileEntries = jar.entries()
                    def classFiles = jarFileEntries.findAll { it.name.endsWith('.class') }

                    classFiles.each {
                        assert it.name.startsWith('org/gradle/testkit')
                    }
                }
            }
        """

        when:
        ExecutionResult result = succeeds('verifyTestKitJars', 'compileTestJava')

        then:
        result.assertTaskNotSkipped(':compileTestJava')
    }

    def "gradleApi dependency API does not include test-kit JAR"() {
        when:
        buildFile << gradleApiDependency()
        buildFile << """
            verifyTestKitJars {
                doLast {
                    def jarFiles = resolveLibs.extractedDir.listFiles()
                    def testKitFunctionalJar = jarFiles.find { it.name.startsWith('$GRADLE_TEST_KIT_JAR_BASE_NAME') }
                    assert !testKitFunctionalJar
                }
            }
        """

        then:
        succeeds('verifyTestKitJars')
    }

    def "gradleApi dependency API cannot compile class that relies on test-kit JAR"() {
        given:
        buildFile << gradleApiDependency()

        when:
        ExecutionResult result = fails('compileTestJava')

        then:
        result.assertTaskNotSkipped(':compileTestJava')
        result.error.contains('package org.gradle.testkit.runner does not exist')
    }

    private String gradleApiDependency() {
        testCompileDependency('gradleApi()')
    }

    private String testKitDependency() {
        testCompileDependency('gradleTestKit()')
    }

    private String testCompileDependency(String dependencyNotation) {
        """
            dependencies {
                testCompile $dependencyNotation
            }
        """
    }

    private String javaClassReferencingTestKit() {
        """package com.gradle.example;

           import org.gradle.testkit.runner.GradleRunner;

           public class MyTest {}
        """
    }
}
