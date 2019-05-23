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

package org.gradle.model.dsl.internal.transform

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.model.dsl.internal.NonTransformedModelDslBacking
import spock.lang.Unroll

import static org.hamcrest.CoreMatchers.containsString

class NestedModelDslUsageIntegrationSpec extends AbstractIntegrationSpec {

    @Unroll
    def "model block can be used in nested context in build script - #code"() {
        given:
        settingsFile << "include 'a', 'b'"

        when:
        buildScript """
            ${testPluginImpl()}

            allprojects { apply type: TestPlugin }

            $code {
                model {
                    strings {
                        add "foo"
                    }
                }
            }

        """

        then:
        succeeds "printStrings"
        output.contains "strings: [foo]"

        where:
        code << [
                "subprojects",
                "project(':a')",
                "if (true)"
        ]
    }

    def "model block can be used from init script"() {
        when:
        file("init.gradle") << """
            ${testPluginImpl()}

            allprojects {
                apply type: TestPlugin

                model {
                    strings {
                        add "foo"
                    }
                }
            }
        """

        then:
        args("-I", file("init.gradle").absolutePath)
        succeeds "printStrings"
        output.contains "strings: [foo]"
    }

    @Unroll
    def "model block rules in nested context cannot reference inputs using dollar method expressions - #code"() {
        given:
        settingsFile << "include 'a', 'b'"

        when:
        buildScript """
            allprojects { apply type: TestPlugin }

            $code {
                model {
                    strings {
                        add \$("foo")
                    }
                }
            }

            ${testPluginImpl()}
        """

        then:
        fails "printStrings"
        failure.assertHasLineNumber(7)
        failure.assertHasCause(NonTransformedModelDslBacking.ATTEMPTED_INPUT_SYNTAX_USED_MESSAGE)

        where:
        code << [
                "subprojects",
                "project(':a')",
                "if (true)"
        ]
    }

    @Unroll
    def "model block rules in nested context cannot reference inputs using dollar path expressions - #code"() {
        given:
        settingsFile << "include 'a', 'b'"

        when:
        buildScript """
            allprojects { apply type: TestPlugin }

            $code {
                model {
                    strings {
                        add \$.foo
                    }
                }
            }

            ${testPluginImpl()}
        """

        then:
        fails "printStrings"
        failure.assertHasLineNumber(7)
        failure.assertThatCause(containsString('Invalid variable name. Must include a letter but only found: $'))

        where:
        code << [
                "subprojects",
                "project(':a')",
                "if (true)"
        ]
    }

    def "model block used in init script cannot reference inputs using dollar method expressions"() {
        when:
        file("init.gradle") << """
            allprojects {
                apply type: TestPlugin

                model {
                     strings {
                        add \$("foo")
                    }
                }
            }

            ${testPluginImpl()}
        """

        then:
        args("-I", file("init.gradle").absolutePath)
        fails "printStrings"
        failure.assertHasLineNumber(7)
        failure.assertHasCause(NonTransformedModelDslBacking.ATTEMPTED_INPUT_SYNTAX_USED_MESSAGE)
    }

    def "model block must receive transformed closure"() {
        when:
        buildScript """
            ${testPluginImpl()}
            apply type: TestPlugin


            def c = {
                strings {
                    add foo
                }
            }

            model(c)
        """

        then:
        fails "tasks"
        failure.assertHasLineNumber 22
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertThatCause(containsString(ModelBlockTransformer.NON_LITERAL_CLOSURE_TO_TOP_LEVEL_MODEL_MESSAGE))
    }

    String testPluginImpl() {
        return """
            class TestPlugin {
                static class Rules extends org.gradle.model.RuleSource {
                    @Model String foo() { "foo" }
                    @Model List<String> strings() { [] }
                    @Mutate void addTask(ModelMap<Task> tasks, List<String> strings) {
                        tasks.create("printStrings") { it.doLast { println "strings: " + strings } }
                    }
                }
            }
        """
    }
}
