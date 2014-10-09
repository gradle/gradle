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
import org.gradle.integtests.fixtures.EnableModelDsl
import spock.lang.Unroll

import static org.hamcrest.Matchers.containsString

class ModelDslRuleDetectionIntegrationSpec extends AbstractIntegrationSpec {

    def setup() {
        EnableModelDsl.enable(executer)
    }

    @Unroll
    def "rules are detected when model path is a straight property reference chain - #path"() {
        given:
        def normalisedPath = path.replace('"', '').replaceAll("'", "")

        when:
        buildScript """
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {}
                @RuleSource
                static class Rules {
                    @Model("$normalisedPath") List<String> strings() {
                        []
                    }

                    @Mutate
                    void addTask(CollectionBuilder<Task> tasks, @Path("$normalisedPath") List<String> strings) {
                        tasks.create("printStrings") {
                            it.doLast {
                                println "strings: " + strings
                            }
                        }
                    }
                }
            }

            apply plugin: MyPlugin

            model {
                $path {
                  add "foo"
                }
            }
        """

        then:
        succeeds "printStrings"
        output.contains("strings: [foo]")

        where:
        path << [
                "a",
                "a.b",
                "a.b.c",
                "a.b.c.d",
                'a."b".c."d"',
                "foo.each",
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
}
