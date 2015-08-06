/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.execution.taskgraph

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class RuleBasedTaskReferenceIntegrationTest extends AbstractIntegrationSpec {

    @NotYetImplemented
    @Unroll
    def "can apply an action to a rule task referenced #reference"() {
        given:
        buildFile << """

        class EchoTask extends DefaultTask {
            String text = "default"

            @TaskAction
            void print() {
                println(name + ' ' + text)
            }
        }

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("actionMan", EchoTask) {
                }
            }
        }

        apply type: Rules

        $reference {
         it.text = 'This is your commander speaking'
        }
        """

        when:
        succeeds 'actionMan'

        then:
        //TODO assumed this should hav failed to begin with (without the '!')
        !output.contains("actionMan This is your commander speaking")

        where:
        reference << ['tasks.withType(EchoTask)', "tasks.matching { it.name == 'actionMan'}.all() "]
    }
}
