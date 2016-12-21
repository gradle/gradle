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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class VerifyNoTaskInputChangesTaskExecutionIntegrationTest extends AbstractIntegrationSpec {

    @Unroll
    def "modifying input #what during execution fails the build"(what, expression) {
        given:
        buildFile << taskDefinition()
        buildFile << """
            someTask.doFirst {
                $expression
            }
        """.stripIndent()

        expect:
        fails '-Dorg.gradle.tasks.verifyinputs=true', 'someTask'
        failure.assertHasDescription('Execution failed for task \':someTask\'.')
        failure.assertHasCause('The inputs for the task changed during the execution! Check if you have a `doFirst` changing the inputs.')

        where:
        what | expression
        'properties' | 'inputProperty = 4'
        'files'      | 'inputFile << "different"'
    }

    def "modifying inputs during execution does not fail the build if the verifier is not enabled"() {
        given:
        buildFile << taskDefinition()
        buildFile << """
            someTask.doFirst {
                inputProperty = 4
            }
        """.stripIndent()

        expect:
        succeeds 'someTask'
    }

    private String taskDefinition() {
        """
            task someTask {
                ext.inputFile = file('input.txt')
                ext.outputFile = file("\$buildDir/output.txt")
                ext.inputProperty = 3                                    
                inputs.file({ inputFile }).withPropertyName('inputFile')
                inputs.property('inputProperty') { inputProperty }
                outputs.file({ outputFile }).withPropertyName('outputFile')
                      
                doLast {              
                    outputFile.parentFile.mkdirs()
                    outputFile.text = "\$inputProperty"
                }
            }
            
        """.stripIndent()
    }
}
