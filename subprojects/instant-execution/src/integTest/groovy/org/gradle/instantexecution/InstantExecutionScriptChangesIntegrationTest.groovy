/*
 * Copyright 2020 the original author or authors.
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

import org.junit.Test
import spock.lang.Unroll

class InstantExecutionScriptChangesIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    @Unroll
    @Test
    def "invalidates cache upon changes to #language build script"() {
        given:
        def instant = newInstantExecutionFixture()
        def buildFile = file("build${language.fileExtension}")

        when:
        buildFile.text = language.defineGreetTask('Hello!')
        instantRun 'greet'

        then:
        outputContains 'Hello!'

        when:
        buildFile.text = language.defineGreetTask('Hi!')
        instantRun 'greet'

        then:
        outputContains 'Hi!'
        instant.assertStateStored()

        when:
        instantRun 'greet'

        then:
        outputContains 'Hi'
        instant.assertStateLoaded()

        where:
        language << ScriptLanguage.values()
    }

    enum ScriptLanguage {

        GROOVY{
            @Override
            String getFileExtension() {
                ".gradle"
            }

            @Override
            String defineGreetTask(String message) {
                """
                    task greet {
                        doLast { println '$message' }
                    }
                """
            }
        },

        KOTLIN{
            @Override
            String getFileExtension() {
                ".gradle.kts"
            }

            @Override
            String defineGreetTask(String message) {
                """
                    task("greet") {
                        doLast { println("$message") }
                    }
                """
            }
        };

        abstract String getFileExtension();

        abstract String defineGreetTask(String message);
    }
}
