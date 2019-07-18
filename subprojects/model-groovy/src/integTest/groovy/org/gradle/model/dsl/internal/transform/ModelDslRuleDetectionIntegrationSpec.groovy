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
import spock.lang.Unroll

import static org.hamcrest.CoreMatchers.containsString

class ModelDslRuleDetectionIntegrationSpec extends AbstractIntegrationSpec {

    @Unroll
    def "rules are detected when model path is a straight property reference chain - #path"() {
        given:
        def normalisedPath = path.replace('"', '').replaceAll("'", "")

        when:
        buildScript """
            @Managed
            interface Item {
                String getValue()
                void setValue(String value)
            }

            @Managed
            interface A extends Item {
                B getB()
            }

            @Managed
            interface B extends Item {
                C getC()
            }

            @Managed
            interface C extends Item {
                D getD()
            }

            @Managed
            interface D extends Item {
            }

            class MyPlugin extends RuleSource {
                @Model
                void a(A a) { }

                @Mutate
                void addTask(ModelMap<Task> tasks, @Path("$normalisedPath") Item item) {
                    tasks.create("printValue") {
                        it.doLast {
                            println "value: " + item.value
                        }
                    }
                }
            }

            apply type: MyPlugin

            model {
                $path {
                  value = "foo"
                }
            }
        """

        then:
        succeeds "printValue"
        output.contains("value: foo")

        where:
        path << [
                "a",
                "a.b",
                "a.b.c",
                "a.b.c.d",
                'a."b".c."d"'
        ]
    }

    @Unroll
    def "only literal property paths are allowed - #pathCode"() {
        when:
        buildScript """
            model {
                $pathCode {

                }
            }
        """

        then:
        fails "tasks"
        failure.assertHasLineNumber 3
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertThatCause(containsString(RulesVisitor.INVALID_STATEMENT))

        where:
        pathCode << [
                '"a" + "a"',
                'foo.bar().baz',
                'foo["bar"]',
                'foo["bar"].baz',
                'def a = b; b',
        ]
    }

    @Unroll
    def "only rules are allowed in the model block - #code"() {
        when:
        buildScript """
            model {
                $code
            }
        """

        then:
        fails "tasks"
        failure.assertHasLineNumber 3
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertThatCause(containsString(RulesVisitor.INVALID_STATEMENT))

        where:
        code << [
                'def a = "foo"',
                'if (true) {}',
                'try {} catch(e) {}',
        ]
    }

    def "only closure literals can be used as rules"() {
        when:
        buildScript """
            class MyPlugin extends RuleSource {
                @Model
                String foo() {
                  "foo"
                }
            }

            apply type: MyPlugin

            def c = {};
            model {
                foo(c)
            }
        """

        then:
        fails "tasks"
        failure.assertHasLineNumber 13
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertThatCause(containsString(RulesVisitor.INVALID_RULE_SIGNATURE))
    }
}
