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

package org.gradle.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl

class MutationRuleApplicationOrderIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildScript '''
            class MutationRecorder {
                def mutations = []
            }

            class EchoTask extends DefaultTask {
                MutationRecorder recorder

                @TaskAction
                void printMessages() {
                    println "mutations: ${recorder.mutations.join(", ")}"
                }
            }
        '''
    }

    def "mutation rules from inner source classes applied via their common parent are executed in the order specified by class names of these rule sources"() {
        when:
        buildFile << '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class MultipleRuleSources implements Plugin<Project> {
                static class B extends RuleSource {
                    @Mutate
                    void b(MutationRecorder recorder) {
                        recorder.mutations << "b"
                    }
                }

                static class A extends RuleSource {
                    @Model
                    MutationRecorder recorder() {
                        new MutationRecorder()
                    }

                    @Mutate
                    void a(MutationRecorder recorder) {
                        recorder.mutations << "a"
                    }

                    @Mutate
                    void addTasks(ModelMap<Task> tasks, MutationRecorder recorderInput) {
                        tasks.create("echo", EchoTask) {
                            recorder = recorderInput
                        }
                    }
                }

                void apply(Project project) {}
            }
            apply type: MultipleRuleSources
        '''

        then:
        succeeds "echo"

        and:
        output.contains "mutations: a, b"
    }

    def "mutation rules are executed in the order of application for rule sources and order of declaration for dsl defined rules"() {
        when:
        EnableModelDsl.enable(executer)
        buildFile << '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class FirstSource extends RuleSource {
                @Mutate
                void first(MutationRecorder recorder, @Path("firstInput") String input) {
                    recorder.mutations << "first source"
                }
            }


            class SecondSource extends RuleSource {
                @Model
                MutationRecorder recorder() {
                    new MutationRecorder()
                }

                @Model
                String secondInput() {
                    ""
                }

                @Mutate
                void second(MutationRecorder recorder, @Path("secondInput") String input) {
                    recorder.mutations << "second source"
                }

                @Mutate
                void addTasks(ModelMap<Task> tasks, MutationRecorder recorderInput) {
                    tasks.create("echo", EchoTask) {
                        recorder = recorderInput
                    }
                }
            }

            class FirstInputProvider extends RuleSource {
                @Model
                String firstInput() {
                    ""
                }
            }

            apply type: FirstSource
            model {
                recorder {
                    $("firstInput")
                    mutations << "first dsl"
                }
                recorder {
                    $("secondInput")
                    mutations << "second dsl"
                }
            }
            apply type: SecondSource
            apply type: FirstInputProvider
        '''

        then:
        succeeds "echo"

        and:
        output.contains "mutations: first source, first dsl, second dsl, second source"
    }
}
