/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.execution

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class VerificationFailureHandlingIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            // 1) add producer and consumer tasks
            // 2) producer throws VerificationException
            // 3) consumer's input is wired to producer's output

            abstract class ProducerTask extends DefaultTask {

                @OutputFile
                abstract RegularFileProperty getProducerOutput()

                @TaskAction
                void doAction() {
                    throw new VerificationException("ProducerTask threw VerificationException")
                }
            }

            def producerTask = tasks.register('producerTask', ProducerTask) {
                producerOutput.convention(project.layout.buildDirectory.file('producerOutput.txt'))
            }

            tasks.register('consumerTask') {
                inputs.file(producerTask.flatMap { it.producerOutput })
            }
        """
    }

    def 'consumer task executes when it has a producer task output dependency and producer task has verification failure, with --continue'() {
        when:
        fails('consumerTask', '--continue')

        then:
        result.assertTaskExecuted(':producerTask')
        result.assertTaskExecuted(':consumerTask')

        when:
        fails('consumerTask', '--continue')

        then:
        result.assertTaskExecuted(':producerTask')
        result.assertTaskExecuted(':consumerTask')
    }

    def 'producer task doLast action does not execute after verification failure is thrown; consumer task does not execute even with --continue'() {
        given:
        buildFile << '''
            tasks.named('producerTask', ProducerTask).configure {
                doLast {
                    throw new RuntimeException('intentional failure in doLast action')
                }
            }
        '''

        expect:
        fails('consumerTask', '--continue')
        result.assertTaskExecuted(':producerTask')
        result.assertTaskNotExecuted(':customTask')
        failure.assertHasCause('ProducerTask threw VerificationException')
        outputDoesNotContain('intentional failure in doLast action')

        fails('consumerTask', '--continue')
    }

}
