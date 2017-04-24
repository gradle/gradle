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
import org.gradle.util.GradleVersion
import spock.lang.Ignore

class DependencyHandlerApiResolveIntegrationTest extends AbstractIntegrationSpec {
    public static final String GRADLE_TEST_KIT_JAR_BASE_NAME = 'gradle-test-kit-'

    def setup() {
        executer.requireGradleDistribution()

        buildFile << """
            apply plugin: 'java'

            task resolveLibs(type: Copy) {
                ext.extractedDir = file("\$buildDir/libs")
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
        buildFile << """
            dependencies {
                testCompile gradleTestKit()
            }

            verifyTestKitJars {
                doLast {
                    def jarFiles = resolveLibs.extractedDir.listFiles()
                    def testKitFunctionalJar = jarFiles.find { it.name.startsWith('$GRADLE_TEST_KIT_JAR_BASE_NAME') }
                    assert testKitFunctionalJar

                    def jar = new java.util.jar.JarFile(testKitFunctionalJar)
                    def classFiles
                    try {
                        classFiles = jar.entries().collect {
                            it.name.startsWith('org/gradle/testkit') && it.name.endsWith('.class')
                        }
                    } finally {
                        jar.close()
                    }

                    assert !classFiles.empty
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
        buildFile << """
            dependencies {
                testCompile gradleApi()
            }
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
        buildFile << """
            dependencies {
                testCompile gradleApi()
            }
        """

        when:
        ExecutionResult result = fails('compileTestJava')

        then:
        result.assertTaskNotSkipped(':compileTestJava')
        result.error.contains('package org.gradle.testkit.runner does not exist')
    }

    @Ignore("Must fix for 4.0")
    def "artifact metadata is available for files added by dependency declarations"() {
        given:
        buildFile << """
            configurations { a; b; c }
            dependencies {
                a gradleApi()
                b gradleTestKit() 
                c localGroovy()
            }
            task showArtifacts {
                doLast {
                    println "gradleApi() files: " + configurations.a.incoming.files.collect { it.name }
                    println "gradleApi() ids: " + configurations.a.incoming.artifacts.collect { it.id }
                    println "gradleTestKit() files: " + configurations.b.incoming.files.collect { it.name }
                    println "gradleTestKit() ids: " + configurations.b.incoming.artifacts.collect { it.id }
                    println "localGroovy() files: " + configurations.c.incoming.files.collect { it.name }
                    println "localGroovy() ids: " + configurations.c.incoming.artifacts.collect { it.id }
                }
            }
"""

        when:
        succeeds("showArtifacts")

        then:
        def gradleVersion = GradleVersion.current().version
        def gradleBaseVersion = GradleVersion.current().baseVersion.version
        def groovyVersion = GroovySystem.version
        outputContains("gradleApi() files: [gradle-api-${gradleVersion}.jar, groovy-all-${groovyVersion}.jar, gradle-installation-beacon-${gradleBaseVersion}.jar]")
        outputContains("gradleApi() ids: [gradle-api-${gradleVersion}.jar (Gradle API), groovy-all-${groovyVersion}.jar (Gradle API), gradle-installation-beacon-${gradleBaseVersion}.jar (Gradle API)]")
        outputContains("gradleTestKit() files: [gradle-test-kit-${gradleVersion}.jar, gradle-api-${ gradleVersion}.jar, groovy-all-${groovyVersion}.jar, gradle-installation-beacon-${gradleBaseVersion}.jar]")
        outputContains("gradleTestKit() ids: [gradle-test-kit-${gradleVersion}.jar (Gradle TestKit), gradle-api-${gradleVersion}.jar (Gradle TestKit), groovy-all-${groovyVersion}.jar (Gradle TestKit), gradle-installation-beacon-${gradleBaseVersion}.jar (Gradle TestKit)]")
        outputContains("localGroovy() files: [groovy-all-${groovyVersion}.jar]")
        outputContains("localGroovy() ids: [groovy-all-${groovyVersion}.jar (Local Groovy)]")
    }

    private String javaClassReferencingTestKit() {
        """package com.gradle.example;

           import org.gradle.testkit.runner.GradleRunner;

           public class MyTest {}
        """
    }
}
