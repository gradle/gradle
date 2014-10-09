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

package org.gradle.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.model.internal.report.AmbiguousBindingReporter
import org.gradle.model.internal.report.IncompatibleTypeReferenceReporter
import org.gradle.model.internal.report.unbound.UnboundRule
import org.gradle.model.internal.report.unbound.UnboundRuleInput

import static org.gradle.model.report.unbound.UnboundRulesReportMatchers.unbound
import static org.gradle.util.TextUtil.normaliseLineSeparators

class ModelRuleBindingFailureIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        EnableModelDsl.enable(executer)
    }

    def "unbound rules are reported"() {
        given:
        buildScript """
            import org.gradle.model.*

            class MyPlugin implements Plugin<Project> {
                void apply(Project p) {

                }

                static class MyThing1 {}
                static class MyThing2 {}
                static class MyThing3 {}

                @RuleSource
                static class Rules {
                    @Model
                    MyThing1 thing1(MyThing2 thing2) {
                        new MyThing1()
                    }


                    @Mutate
                    void mutateThing2(MyThing2 thing2, MyThing3 thing3) {

                    }
                }
            }

            apply plugin: MyPlugin
        """

        when:
        fails "tasks"

        then:
        failure.assertThatCause(unbound(
                UnboundRule.descriptor('MyPlugin$Rules#thing1(MyPlugin$MyThing2)')
                        .immutableInput(UnboundRuleInput.type('MyPlugin$MyThing2').description("parameter 1")),
                UnboundRule.descriptor('MyPlugin$Rules#mutateThing2(MyPlugin$MyThing2, MyPlugin$MyThing3)')
                        .mutableInput(UnboundRuleInput.type('MyPlugin$MyThing2').description("parameter 1"))
                        .immutableInput(UnboundRuleInput.type('MyPlugin$MyThing3').description("parameter 2"))
        ))
    }

    def "unbound dsl rules are reported"() {
        given:
        buildScript """

            model {
                foo.bar {

                }
            }
        """

        when:
        fails "tasks"

        then:
        failure.assertThatCause(unbound(
                UnboundRule.descriptor("model.foo.bar", buildFile, 4, 17)
                        .mutableInput(UnboundRuleInput.type(Object).path('foo.bar'))
        ))
    }

    def "suggestions are provided for unbound rules"() {
        given:
        buildScript """
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {}

                @RuleSource
                static class Rules {
                    @Mutate
                    void addTasks(CollectionBuilder<Task> tasks) {
                        tasks.create("foobar")
                        tasks.create("raboof")
                    }
                }
            }

            apply plugin: MyPlugin

            model {
                tasks.foonar {
                }
            }
        """

        when:
        fails "tasks"


        then:
        failure.assertThatCause(unbound(
                UnboundRule.descriptor("model.tasks.foonar", buildFile, 21, 17)
                        .mutableInput(UnboundRuleInput.type(Object).path("tasks.foonar").suggestions("tasks.foobar"))
        ))
    }

    def "ambiguous binding integration test"() {
        given:
        buildScript """
            import org.gradle.model.*

            class Plugin1 implements Plugin {
                void apply(plugin) {}
                @RuleSource
                static class Rules {
                    @Model
                    String s1() {
                        "foo"
                    }
                }
            }

            class Plugin2 implements Plugin {
                void apply(plugin) {}
                @RuleSource
                static class Rules {
                    @Model
                    String s2() {
                        "bar"
                    }
                }
            }

            class Plugin3 implements Plugin {
                void apply(plugin) {}
                @RuleSource
                static class Rules {
                    @Mutate
                    void m(String s) {
                        "foo"
                    }
                }
            }

            apply plugin: Plugin1
            apply plugin: Plugin2
            apply plugin: Plugin3
        """

        when:
        fails "tasks"

        then:
        failure.assertHasCause("Failed to apply plugin [class 'Plugin3']")
        failure.assertHasCause("There is a problem with model rule Plugin3\$Rules#m(java.lang.String).")
        failure.assertHasCause(normaliseLineSeparators(
                new AmbiguousBindingReporter(String.name, "parameter 1", [
                        new AmbiguousBindingReporter.Provider("s2", "Plugin2\$Rules#s2()"),
                        new AmbiguousBindingReporter.Provider("s1", "Plugin1\$Rules#s1()")
                ]).asString()
        ))
    }

    def "incompatible type binding"() {
        given:
        buildScript """
            import org.gradle.model.*

            class Plugin1 implements Plugin {
                void apply(plugin) {}
                @RuleSource
                static class Rules {
                    @Mutate
                    void addTasks(@Path("tasks") Integer s1) {

                    }
                }
            }

            apply plugin: Plugin1
        """

        when:
        fails "tasks"

        then:
        failure.assertHasCause("Failed to apply plugin [class 'Plugin1']")
        failure.assertHasCause("There is a problem with model rule Plugin1\$Rules#addTasks(java.lang.Integer).")
        failure.assertHasCause(normaliseLineSeparators(
                new IncompatibleTypeReferenceReporter(
                        "Project.<init>.tasks()",
                        "tasks",
                        Integer.name,
                        "parameter 1",
                        true,
                        [
                                "org.gradle.api.tasks.TaskContainer (or assignment compatible type thereof)",
                                "org.gradle.model.collection.CollectionBuilder<org.gradle.api.Task>"
                        ]
                ).asString()
        ))
    }
}
