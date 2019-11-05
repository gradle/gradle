/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

class InstantExecutionKotlinIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def kotlinVersion = '1.3.70-dev-1099'

    def setup() {
        executer.noDeprecationChecks()
    }

    def kotlinDev() {
        'maven { url = "https://dl.bintray.com/kotlin/kotlin-dev/" }'
    }

    def "compileKotlin"() {

        def instantExecution = newInstantExecutionFixture()

        given:
        buildFile << """
            buildscript {
                repositories {
                    ${kotlinDev()}
                    ${mavenCentralRepository()}
                }
                dependencies {
                    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${kotlinVersion}")
                }
            }

            apply plugin: 'org.jetbrains.kotlin.jvm'

            repositories {
                ${kotlinDev()}
                ${mavenCentralRepository()}
            }
        """
        file("src/main/kotlin/Thing.kt") << """
            class Thing
        """

        and:
        def expectedTasks = [
            ":compileKotlin"
        ]
        def classFile = file("build/classes/kotlin/main/Thing.class")

        when:
        instantRun "compileKotlin", "--info"

        then:
        instantExecution.assertStateStored()
        result.assertTasksExecuted(*expectedTasks)

        and:
        classFile.isFile()

        when:
        classFile.delete()

        and:
        instantRun "compileKotlin", "--info", "-s"

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksExecuted(*expectedTasks)

        and:
        classFile.isFile()
    }

    def "build on Kotlin build with JUnit tests"() {

        def instantExecution = newInstantExecutionFixture()

        given:
        buildFile << """
            buildscript {
                repositories {
                    ${kotlinDev()}
                    ${mavenCentralRepository()}
                }
                dependencies {
                    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${kotlinVersion}")
                }
            }

            apply plugin: 'org.jetbrains.kotlin.jvm'

            repositories {
                ${kotlinDev()}
                ${mavenCentralRepository()}
            }

            dependencies {
                testImplementation("junit:junit:4.12")
            }
        """
        file("src/main/kotlin/Thing.kt") << """
            class Thing
        """
        file("src/test/kotlin/ThingTest.kt") << """
            import org.junit.*
            class ThingTest {
                @Test fun ok() { Thing() }
            }
        """

        and:
        def expectedTasks = [
            ":assemble", ":build", ":check", ":classes", ":compileJava",
            ":compileKotlin", ":compileTestJava", ":compileTestKotlin", ":inspectClassesForKotlinIC",
            ":jar", ":processResources", ":processTestResources", ":test", ":testClasses"
        ]
        def classFile = file("build/classes/kotlin/main/Thing.class")
        def testClassFile = file("build/classes/kotlin/test/ThingTest.class")
        def testResults = file("build/test-results/test")

        when:
        instantRun "build"

        then:
        instantExecution.assertStateStored()
        result.assertTasksExecuted(*expectedTasks)

        and:
        classFile.isFile()
        testClassFile.isFile()
        testResults.isDirectory()

        and:
        assertTestsExecuted("ThingTest", "ok")

        when:
        classFile.delete()
        testClassFile.delete()
        testResults.delete()

        and:
        instantRun "build"

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksExecuted(*expectedTasks)

        and:
        classFile.isFile()
        testClassFile.isFile()
        testResults.isDirectory()

        and:
        assertTestsExecuted("ThingTest", "ok")
    }
}
