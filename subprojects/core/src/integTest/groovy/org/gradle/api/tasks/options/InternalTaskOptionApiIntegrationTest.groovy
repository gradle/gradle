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

package org.gradle.api.tasks.options

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class InternalTaskOptionApiIntegrationTest extends AbstractIntegrationSpec {

    def "renders deprecation message when internal @Option and @OptionValues are used"() {
        given:
        executer.expectDeprecationWarnings(2)
        buildFile << """
            task someTask(type: SomeTask)
            
            ${taskUsingInternalApi()}
        """

        when:
        run 'someTask', '--option=foo'

        then:
        outputContains('option=foo')
        outputContains('org.gradle.api.internal.tasks.options.OptionValues has been deprecated. This is scheduled to be removed in Gradle 5.0. Use org.gradle.api.tasks.options.OptionValues instead.')
        outputContains('org.gradle.api.internal.tasks.options.Option has been deprecated. This is scheduled to be removed in Gradle 5.0. Use org.gradle.api.tasks.options.Option instead.')
    }

    static String taskUsingInternalApi() {
        """
            import org.gradle.api.internal.tasks.options.Option
            import org.gradle.api.internal.tasks.options.OptionValues
        
            class SomeTask extends DefaultTask {
                String option

                @Option(option = "option", description = "Configures 'option' field.")
                void setOption(String option) {
                    this.option = option
                }

                @OptionValues("option")
                List<String> availableOptionValues() {
                    ['foo', 'bar']
                }

                @TaskAction
                void renderOptionValue() {
                    println "option=\$option"
                }
            }
        """
    }
}
