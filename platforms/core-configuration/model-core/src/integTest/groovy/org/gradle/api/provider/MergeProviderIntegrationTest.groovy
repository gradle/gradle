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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Tests {@link org.gradle.api.internal.provider.MergeProvider}.
 */
class MergeProviderIntegrationTest extends AbstractIntegrationSpec {

    def "carries task dependencies"() {
        buildFile << """
            tasks.register('myTask1', StringTask) {
                string.set('Hello')
            }
            tasks.register('myTask2', StringTask) {
                string.set('World')
            }

            tasks.register('combined', StringListTask) {
                strings.set(new org.gradle.api.internal.provider.MergeProvider([
                    tasks.named('myTask1').map { it.string.get() },
                    tasks.named('myTask2').map { it.string.get() }
                ]))
            }

            abstract class StringTask extends DefaultTask {
                @Input
                abstract Property<String> getString()

                @TaskAction
                void printText() {
                    println getString().get()
                }
            }

            abstract class StringListTask extends DefaultTask {
                @Input
                abstract ListProperty<String> getStrings()

                @TaskAction
                void printText() {
                    println getStrings().get()
                }
            }
        """

        when:
        succeeds 'combined'

        then:
        executedAndNotSkipped(':myTask1', ':myTask2', ':combined')
        outputContains('[Hello, World]')
    }
}
