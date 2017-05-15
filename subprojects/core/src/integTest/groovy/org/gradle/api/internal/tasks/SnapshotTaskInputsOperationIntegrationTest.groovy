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

package org.gradle.api.internal.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture

class SnapshotTaskInputsOperationIntegrationTest extends AbstractIntegrationSpec {

    private static final String BUILD_OPERATION = 'Compute task input hashes and build cache key'

    def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    def "task output caching key is exposed when build cache is enabled"() {
        given:
        executer.withBuildCacheEnabled()

        when:
        buildFile << customTaskCode('foo', 'bar')
        succeeds('customTask')

        then:
        def result = buildOperationResult()

        then:
        result.containsKey("buildCacheKey")
        result.inputHashes.keySet() == ['input1', 'input2'] as Set
        result.outputPropertyNames == ['outputFile1', 'outputFile2']
    }

    def "task output caching key is not exposed when build cache is disabled"() {
        when:
        buildFile << customTaskCode('foo', 'bar')
        succeeds('customTask')

        then:
        !buildOperations.hasOperation(BUILD_OPERATION)
    }

    private static String customTaskCode(String input1, String input2) {
        """
            @CacheableTask
            class CustomTask extends DefaultTask {
                @Input
                String input2
                @Input
                String input1
                @OutputFile
                File outputFile2 = new File(temporaryDir, "output2.txt")
                @OutputFile
                File outputFile1 = new File(temporaryDir, "output1.txt")

                @TaskAction
                void generate() {
                    outputFile1.text = "done1"
                    outputFile2.text = "done2"
                }
            }

            task customTask(type: CustomTask){
                input1 = '$input1'
                input2 = '$input2'
            }
        """
    }

    Map<String, ?> buildOperationResult() {
        buildOperations.operation(SnapshotTaskInputsOperationDetails).result
    }

}
