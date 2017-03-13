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

class ProviderIntegrationTest extends AbstractIntegrationSpec {

    static final String DEFAULT_TEXT = 'default'
    static final String CUSTOM_TEXT = 'custom'

    def "can create provider and retrieve immutable value"() {
        given:
        buildFile << """
            task myTask(type: MyTask)

            class MyTask extends DefaultTask {
                final Provider<String> text = project.provider { '$DEFAULT_TEXT' }

                String getText() {
                    text.get()
                }

                @TaskAction
                void printText() {
                    println getText()
                }
            }
        """

        when:
        succeeds('myTask')

        then:
        outputContains(DEFAULT_TEXT)
    }

    def "can assign non-final provider to configure default field value"() {
        given:
        buildFile << """
            task myTask(type: MyTask) {
                text = project.provider { '$CUSTOM_TEXT' }
            }

            class MyTask extends DefaultTask {
                Provider<String> text = project.provider { '$DEFAULT_TEXT' }

                void setText(Provider<String> text) {
                    this.text = text
                }

                String getText() {
                    text.get()
                }

                @TaskAction
                void printText() {
                    println getText()
                }
            }
        """

        when:
        succeeds('myTask')

        then:
        outputContains(CUSTOM_TEXT)
    }
}
