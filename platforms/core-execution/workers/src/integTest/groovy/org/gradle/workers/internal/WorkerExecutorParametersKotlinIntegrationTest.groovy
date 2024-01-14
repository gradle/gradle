/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.workers.internal

import groovy.transform.Canonical
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.gradle.workers.fixtures.WorkerExecutorFixture.IsolationMode
import spock.lang.Issue

class WorkerExecutorParametersKotlinIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        withLocalBuildCache()
    }

    @Issue('https://github.com/gradle/gradle/issues/26596')
    def "can provide primitive #primitiveType array parameter with #isolationMode isolation"() {
        given:
        withRunWorkTaskOfPrimitiveArrayProperty isolationMode, primitiveType

        when: 'task runs for the 1st time'
        runWork()

        then: 'it runs as expected'
        outputContains expectedOutput

        when: 'task runs for the 2nd time'
        runWork()

        then: 'it is skipped because UP-TO-DATE'
        result.assertTaskSkipped ':runWork'
        outputContains ':runWork UP-TO-DATE'

        and:
        outputDoesNotContain expectedOutput

        when: 'the outputs are deleted'
        file('build').deleteDir()

        and: 'task runs for the 3rd time'
        runWork()

        then: 'it is skipped because FROM-CACHE'
        result.assertTaskSkipped ':runWork'
        outputContains ':runWork FROM-CACHE'

        and:
        outputDoesNotContain expectedOutput

        where:
        [isolationMode, primitiveType] << [isolationModes(), primitiveTypes()].combinations()
        expectedOutput = expectedOutputFor(primitiveType)
    }

    @Issue('https://github.com/gradle/gradle/issues/26596')
    def "can provide list of primitive #primitiveType array parameter with #isolationMode isolation"() {
        given:
        withRunWorkTaskOfPrimitiveArrayListProperty isolationMode, primitiveType

        when: 'task runs for the 1st time'
        runWork()

        then: 'it runs as expected'
        outputContains expectedOutput

        when: 'task runs for the 2nd time'
        runWork()

        then: 'it is skipped because UP-TO-DATE'
        result.assertTaskSkipped ':runWork'
        outputContains ':runWork UP-TO-DATE'

        and:
        outputDoesNotContain expectedOutput

        when: 'the outputs are deleted'
        file('build').deleteDir()

        and: 'task runs for the 3rd time'
        runWork()

        then: 'it is skipped because FROM-CACHE'
        result.assertTaskSkipped ':runWork'
        outputContains ':runWork FROM-CACHE'

        and:
        outputDoesNotContain expectedOutput

        where:
        [isolationMode, primitiveType] << [isolationModes(), primitiveTypes()].combinations()
        expectedOutput = expectedOutputFor(primitiveType).with { "$it, $it" }
    }

    private ExecutionResult runWork() {
        succeeds 'runWork', '--build-cache'
    }

    private static IsolationMode[] isolationModes() {
        IsolationMode.values()
    }

    private static List<String> primitiveTypes() {
        ['byte', 'short', 'int', 'long', 'float', 'double', 'char', 'boolean']
    }

    private static String expectedOutputFor(String type) {
        switch (type) {
            case 'boolean':
                return 'booleanArrayOf(true)'
            case 'char':
                return 'charArrayOf(*)' // 42.toChar() == '*'
            case 'float':
            case 'double':
                return "${type}ArrayOf(42.0)"
            default:
                return "${type}ArrayOf(42)"
        }
    }

    @Canonical
    static class KotlinPrimitiveArrayFixture {
        String arrayType
        String arrayValue

        static KotlinPrimitiveArrayFixture of(String primitiveType) {
            def kotlinType = primitiveType.toString().capitalize()
            def arrayType = "${kotlinType}Array"
            def primitiveValue = primitiveType == 'boolean'
                ? 'true'
                : "42.to${kotlinType}()"
            def arrayValue = "${primitiveType}ArrayOf(${primitiveValue})"
            new KotlinPrimitiveArrayFixture(arrayType, arrayValue)
        }
    }

    private TestFile withRunWorkTaskOfPrimitiveArrayProperty(IsolationMode isolationMode, String primitiveType) {
        def fixture = KotlinPrimitiveArrayFixture.of(primitiveType)
        def propertyType = "Property<$fixture.arrayType>"
        def propertyValue = fixture.arrayValue
        withRunTaskOf(isolationMode, fixture.arrayType, propertyType, propertyValue, "arrayToString(it)")
    }

    private TestFile withRunWorkTaskOfPrimitiveArrayListProperty(IsolationMode isolationMode, String primitiveType) {
        def fixture = KotlinPrimitiveArrayFixture.of(primitiveType)
        def propertyType = "ListProperty<$fixture.arrayType>"
        def propertyValue = "listOf($fixture.arrayValue, $fixture.arrayValue)"
        withRunTaskOf(isolationMode, fixture.arrayType, propertyType, propertyValue, "it.joinToString { a -> arrayToString(a) }")
    }

    private TestFile withRunTaskOf(
        IsolationMode isolationMode,
        String kotlinArrayType,
        String propertyType,
        String propertyValue,
        String toString
    ) {
        buildKotlinFile << """
            import org.gradle.workers.WorkAction
            import org.gradle.workers.WorkParameters
            import org.gradle.workers.WorkerExecutor

            interface TestParameters : WorkParameters {
                @get:Input
                val workInput: $propertyType
            }

            abstract class TestWorkAction : WorkAction<TestParameters> {
                override fun execute() {
                    println(parameters.workInput.get().let { $toString })
                }

                fun arrayToString(array: $kotlinArrayType) =
                    array.javaClass.componentType.toString() + "ArrayOf" + array.joinToString(",", "(", ")")
            }

            @CacheableTask
            abstract class ParameterTask : DefaultTask() {

                @get:Inject
                abstract val workerExecutor: WorkerExecutor

                @get:Input
                abstract val taskInput: $propertyType

                @get:OutputFile
                abstract val outputFile: RegularFileProperty

                @TaskAction
                fun doWork() {
                    workerExecutor.${isolationMode.method}().submit(TestWorkAction::class) {
                        workInput = taskInput
                    }
                    outputFile.get().asFile.writeText("done")
                }
            }

            tasks {
                register<ParameterTask>("runWork") {
                    taskInput = $propertyValue
                    outputFile = layout.buildDirectory.file("receipt")
                }
            }
        """
    }

    private void withLocalBuildCache() {
        def cacheDir = createDir("cache-dir")
        settingsFile """
            buildCache {
                local {
                    directory = file("${cacheDir.name}")
                }
            }
        """
    }
}
