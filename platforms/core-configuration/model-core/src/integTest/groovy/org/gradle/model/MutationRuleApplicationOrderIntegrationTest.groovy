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
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class MutationRuleApplicationOrderIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildScript '''
            class MutationRecorder {
                def mutations = []
            }

            class EchoTask extends DefaultTask {
                @Internal
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

    def "mutation rules are executed in a fixed and arbitrary order"() {
        when:
        buildFile << '''
            class FirstSource extends RuleSource {
                @Mutate
                void first(MutationRecorder recorder) {
                    recorder.mutations << "first source"
                }
            }

            class SecondSource extends RuleSource {
                @Model
                MutationRecorder recorder() {
                    new MutationRecorder()
                }

                @Mutate
                void second(@Path("recorder") MutationRecorder recorder) {
                    recorder.mutations << "second source"
                }

                @Mutate
                void third(MutationRecorder recorder) {
                    recorder.mutations << "third source"
                }

                @Mutate
                void addTasks(ModelMap<Task> tasks, MutationRecorder recorderInput) {
                    tasks.create("echo", EchoTask) {
                        recorder = recorderInput
                    }
                }
            }

            apply type: FirstSource
            model {
                recorder {
                    mutations << "first dsl"
                }
            }
            apply type: SecondSource
            model {
                recorder {
                    mutations << "second dsl"
                }
            }
        '''

        then:
        succeeds "echo"

        and:
        output.contains "mutations: second source, first source, third source, first dsl, second dsl"
    }

    def "DSL rules are executed in order declared"() {
        when:
        buildFile << '''
            class FirstSource extends RuleSource {
                @Model
                MutationRecorder recorder() {
                    new MutationRecorder()
                }
            }

            model {
                tasks {
                    echo(EchoTask) {
                        recorder = $.recorder
                    }
                }
                recorder {
                    mutations << "first dsl"
                }
                recorder {
                    mutations << "second dsl"
                }
            }
            apply type: FirstSource
            model {
                recorder {
                    mutations << "third dsl"
                }
                recorder {
                    mutations << "fourth dsl"
                }
            }
        '''

        then:
        succeeds "echo"

        and:
        output.contains "mutations: first dsl, second dsl, third dsl, fourth dsl"
    }
}
