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

package org.gradle.model.managed

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl

class ManagedListIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        EnableModelDsl.enable(executer)
    }

    def "rule can mutate a managed type with a list of scalar read-only property"() {
        given:
        buildScript '''

        @Managed
        interface Container {
            List<String> getItems()
        }

        class Rules extends RuleSource {
            @Model
            void createContainer(Container c) {}

            @Mutate
            void addItems(Container c) {
                c.items.add 'foo'
            }

            @Mutate
            void addCheckTask(ModelMap<Task> tasks, Container c) {
                tasks.create('check') {
                    assert c.items == ['foo']
                }
            }
        }

        apply plugin: Rules
        '''

        expect:
        succeeds 'check'
    }

    def "rule can mutate a managed type with a list of scalar read-write property"() {
        given:
        buildScript '''

        @Managed
        interface Container {
            List<String> getItems()
            void setItems(List<String> items)
        }

        class Rules extends RuleSource {
            static final List<String> INITIAL = ['initial']

            @Model
            void createContainer(Container c) {
                assert c.items == null
                c.items = INITIAL
            }

            @Mutate
            void addItems(Container c) {
                assert !c.items.is(INITIAL)
                c.items.add 'foo'
            }

            @Mutate
            void addCheckTask(ModelMap<Task> tasks, Container c) {
                tasks.create('check') {
                    assert c.items == ['initial','foo']
                }
            }
        }

        apply plugin: Rules
        '''

        expect:
        succeeds 'check'
    }

    def "rule can nullify a managed type with a list of scalar read-write property"() {
        given:
        buildScript '''

        @Managed
        interface Container {
            List<String> getItems()
            void setItems(List<String> items)
        }

        class Rules extends RuleSource {
            static final List<String> INITIAL = ['initial']

            @Model
            void createContainer(Container c) {
                assert c.items == null
                c.items = INITIAL
            }

            @Mutate
            void nullify(Container c) {
                c.items = null
            }

            @Mutate
            void addCheckTask(ModelMap<Task> tasks, Container c) {
                tasks.create('check') {
                    assert c.items == null
                }
            }
        }

        apply plugin: Rules
        '''

        expect:
        succeeds 'check'
    }

    def "rule can overwrite value of a managed type with a list of scalar read-write property"() {
        given:
        buildScript '''

        @Managed
        interface Container {
            List<String> getItems()
            void setItems(List<String> items)
        }

        class Rules extends RuleSource {
            static final List<String> INITIAL = ['initial']

            @Model
            void createContainer(Container c) {
                assert c.items == null
                c.items = INITIAL
            }

            @Mutate
            void nullify(Container c) {
                c.items = ['b','c']
            }

            @Mutate
            void addCheckTask(ModelMap<Task> tasks, Container c) {
                tasks.create('check') {
                    assert c.items == ['b','c']
                }
            }
        }

        apply plugin: Rules
        '''

        expect:
        succeeds 'check'
    }

    def "rule can nullify and set value of a managed type in the same mutation block"() {
        given:
        buildScript '''

        @Managed
        interface Container {
            List<String> getItems()
            void setItems(List<String> items)
        }

        class Rules extends RuleSource {
            static final List<String> INITIAL = ['initial']

            @Model
            void createContainer(Container c) {
                assert c.items == null
                c.items = INITIAL
            }

            @Mutate
            void nullify(Container c) {
                c.items = null
                c.items = ['b','c']
            }

            @Mutate
            void addCheckTask(ModelMap<Task> tasks, Container c) {
                tasks.create('check') {
                    assert c.items == ['b','c']
                }
            }
        }

        apply plugin: Rules
        '''

        expect:
        succeeds 'check'
    }

    def "rule cannot mutate a managed type with a list of scalar property when not the subject of the rule"() {
        when:
        buildScript '''

        @Managed
        interface Container {
            List<String> getItems()
        }

        class Rules extends RuleSource {
            @Model
            void createContainer(Container c) {}

            @Mutate
            void addItems(Container c) {
                c.items.add 'foo'
            }

            @Mutate
            void tryToMutate(ModelMap<Task> map, Container c) {
                c.items.add 'foo'
            }
        }

        apply plugin: Rules
        '''

        then:
        fails 'tasks'

        and:
        failure.assertHasCause "Attempt to mutate closed view of model of type 'java.util.List<java.lang.String>' given to rule 'Rules#tryToMutate'"

    }

    def "rule cannot mutate closed view even using iterator"() {
        when:
        buildScript '''

        @Managed
        interface Container {
            List<String> getItems()
        }

        class Rules extends RuleSource {
            @Model
            void createContainer(Container c) {}

            @Mutate
            void addItems(Container c) {
                c.items.add 'foo'
            }

            @Mutate
            void tryToMutate(ModelMap<Task> map, Container c) {
                def it = c.items.iterator()
                it.next()
                it.remove()
            }
        }

        apply plugin: Rules
        '''

        then:
        fails 'tasks'

        and:
        failure.assertHasCause "Attempt to mutate closed view of model of type 'java.util.List<java.lang.String>' given to rule 'Rules#tryToMutate'"

    }
}
