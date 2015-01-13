/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskSelectiveCreationIntegrationTest extends AbstractIntegrationSpec {

    def "tasks declared via rule are not created unless needed"() {
        given:
        buildFile << """
            def created = []
            model {
                tasks {
                    create("t1") {
                      created << "t1"
                      description = "task t1"
                    }
                    create("t2") {
                      created << "t2"
                      description = "task t2"
                    }
                    create("echo") {
                      doLast {
                        println "created: \$created"
                      }
                    }
                }
            }
        """

        when:
        succeeds "tasks"

        then:
        output.contains "t1 - task t1"
        output.contains "t2 - task t2"

        when:
        succeeds "echo"

        then:
        output.contains "created: []"

        when:
        succeeds "t1", "echo"

        then:
        output.contains "created: [t1]"

        when:
        succeeds "t2", "echo"

        then:
        output.contains "created: [t2]"

        when:
        succeeds "t2", "t1", "echo"

        then:
        output.contains "created: [t2, t1]"
    }

}
