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

package org.gradle.api.internal.tasks.execution

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class InputOutputEnsuringTaskExecuterIntegrationTest extends AbstractIntegrationSpec {
    def "config action is executed immediately before task"() {
        file("input.txt") << "Text"
        buildFile << """
            task myTask {
                inputs.configure {
                    it.configure {
                        println "Inputs are configured via nested configure()"
                    }
                    it.includeFile("input.txt")
                }
                outputs.configure {
                    it.configure {
                        println "Outputs are configured via nested configure()"
                    }
                    it.file("output.txt")
                }
                doLast {
                    file("output.txt") << file("input.txt")
                }
            }
        """

        when:
        succeeds "tasks"
        then:
        !output.contains("Inputs are configured via nested configure()")
        !output.contains("Outputs are configured via nested configure()")

        when:
        succeeds "myTask"
        then:
        output.contains("Inputs are configured via nested configure()")
        output.contains("Outputs are configured via nested configure()")
    }
}
