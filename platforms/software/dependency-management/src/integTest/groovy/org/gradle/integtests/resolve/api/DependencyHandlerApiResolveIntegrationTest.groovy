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

package org.gradle.integtests.resolve.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.GradleVersion

class DependencyHandlerApiResolveIntegrationTest extends AbstractIntegrationSpec {
    public static final String GRADLE_TEST_KIT_JAR_BASE_NAME = 'gradle-test-kit-'

    def setup() {
        buildFile << """
            apply plugin: 'java'

            def libsDir = file("\$buildDir/libs")
            task resolveLibs(type: Copy) {
                from configurations.testCompileClasspath
                into libsDir
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
                testImplementation gradleTestKit()
            }

            verifyTestKitJars {
                doLast {
                    def jarFiles = libsDir.listFiles()
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
                testImplementation gradleApi()
            }
            verifyTestKitJars {
                doLast {
                    def jarFiles = libsDir.listFiles()
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
                testImplementation gradleApi()
            }
        """

        when:
        ExecutionResult result = fails('compileTestJava')

        then:
        result.assertTaskNotSkipped(':compileTestJava')
        result.assertHasErrorOutput('package org.gradle.testkit.runner does not exist')
    }

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "Uses a different classpath when embedded")
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
                def filesA = configurations.a
                def idsA = configurations.a.incoming.artifacts.resolvedArtifacts.map { it.id }
                def filesB = configurations.b
                def idsB = configurations.b.incoming.artifacts.resolvedArtifacts.map { it.id }
                def filesC = configurations.c
                def idsC = configurations.c.incoming.artifacts.resolvedArtifacts.map { it.id }
                doLast {
                    println "gradleApi() files: " + filesA.collect { it.name }
                    println "gradleApi() ids: " + idsA.get()
                    println "gradleTestKit() files: " + filesB.collect { it.name }
                    println "gradleTestKit() ids: " + idsB.get()
                    println "localGroovy() files: " + filesC.collect { it.name }
                    println "localGroovy() ids: " + idsC.get()
                }
            }
"""

        when:
        succeeds("showArtifacts")

        then:
        def gradleVersion = GradleVersion.current().version
        def gradleBaseVersion = GradleVersion.current().baseVersion.version
        def groovyVersion = GroovySystem.version
        def kotlinVersion = getGradleKotlinVersion()
        def groovyModules = ["groovy-${groovyVersion}.jar", "groovy-ant-${groovyVersion}.jar", "groovy-astbuilder-${groovyVersion}.jar", "groovy-console-${groovyVersion}.jar", "groovy-datetime-${groovyVersion}.jar", "groovy-dateutil-${groovyVersion}.jar", "groovy-groovydoc-${groovyVersion}.jar", "groovy-json-${groovyVersion}.jar", "groovy-nio-${groovyVersion}.jar", "groovy-sql-${groovyVersion}.jar", "groovy-templates-${groovyVersion}.jar", "groovy-test-${groovyVersion}.jar", "groovy-xml-${groovyVersion}.jar", "javaparser-core-3.17.0.jar"]
        def expectedGradleApiFiles = "gradle-api-${gradleVersion}.jar, ${groovyModules.join(", ")}, kotlin-stdlib-${kotlinVersion}.jar, kotlin-stdlib-common-${kotlinVersion}.jar, kotlin-reflect-${kotlinVersion}.jar, gradle-installation-beacon-${gradleBaseVersion}.jar"
        def expectedGradleApiIds = { id ->
            "gradle-api-${gradleVersion}.jar ($id), ${groovyModules.collect({ it + " ($id)" }).join(", ")}, kotlin-stdlib-${kotlinVersion}.jar ($id), kotlin-stdlib-common-${kotlinVersion}.jar ($id), kotlin-reflect-${kotlinVersion}.jar ($id), gradle-installation-beacon-${gradleBaseVersion}.jar ($id)"
        }
        outputContains("gradleApi() files: [$expectedGradleApiFiles]")
        outputContains("gradleApi() ids: [${expectedGradleApiIds("Gradle API")}]")
        outputContains("gradleTestKit() files: [gradle-test-kit-${gradleVersion}.jar, $expectedGradleApiFiles]")
        outputContains("gradleTestKit() ids: [gradle-test-kit-${gradleVersion}.jar (Gradle TestKit), ${expectedGradleApiIds("Gradle TestKit")}]")
        outputContains("localGroovy() files: [${groovyModules.join(", ")}]")
        outputContains("localGroovy() ids: [${groovyModules.collect({ it + " (Local Groovy)" }).join(", ")}]")
    }

    private static String getGradleKotlinVersion() {
        def props = new Properties()
        def resource = DependencyHandlerApiResolveIntegrationTest.getResource("/gradle-kotlin-dsl-versions.properties")
        props.load(new StringReader(resource.text))
        return props.kotlin
    }

    private static String javaClassReferencingTestKit() {
        """package com.gradle.example;

           import org.gradle.testkit.runner.GradleRunner;

           public class MyTest {}
        """
    }
}
