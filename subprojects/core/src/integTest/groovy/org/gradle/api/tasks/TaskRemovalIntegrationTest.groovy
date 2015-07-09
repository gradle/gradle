/*
 * Copyright 2013 the original author or authors.
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
import org.hamcrest.Matchers
import spock.lang.Unroll

class TaskRemovalIntegrationTest extends AbstractIntegrationSpec {

    def "can remove task"() {
        given:
        buildScript """
            task foo {}
            tasks.remove(foo)
            task foo {}
            tasks.remove(foo)
        """

        when:
        fails "foo"

        then:
        failure.assertThatDescription(Matchers.startsWith("Task 'foo' not found in root project"))
    }

    def "can remove task in after evaluate"() {
        given:
        buildScript """
            task foo {}
            afterEvaluate {
                tasks.remove(foo)
            }
        """

        when:
        fails "foo"

        then:
        failure.assertThatDescription(Matchers.startsWith("Task 'foo' not found in root project"))
    }

    @Unroll
    def "can remove task in after evaluate if task is used by unbound #annotationClass rule"() {
        given:
        buildScript """
            task foo {}

            afterEvaluate {
                tasks.remove(foo)
            }

            class Rules extends RuleSource {
                @$annotationClass
                void linkFooToBar(String bar, @Path("tasks.foo") Task foo) {
                   // do nothing
                }
            }

            apply plugin: Rules
        """

        when:
        fails "dependencies"

        then:
        failure.assertThatCause(Matchers.startsWith("The following model rules could not be applied"))

        where:
        annotationClass << ["Defaults", "Mutate", "Finalize", "Validate"]
    }

    def "cant remove task if used by rule"() {
        when:
        buildScript """
            task foo {}
            task bar { doLast { tasks.remove(foo) } }

            class Rules extends RuleSource {
                @Mutate
                void linkFooToBar(@Path("tasks.bar") Task bar, @Path("tasks.foo") Task foo) {
                   // do nothing
                }
            }

            apply plugin: Rules
        """

        then:
        fails ":bar"
        failure.assertThatCause(Matchers.startsWith("Tried to remove model tasks.foo but it is depended on by: tasks.bar"))

    }
}
