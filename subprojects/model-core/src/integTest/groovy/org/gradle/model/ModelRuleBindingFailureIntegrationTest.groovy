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
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.report.AmbiguousBindingReporter
import org.gradle.model.internal.report.UnboundRuleReportOutputBuilder
import org.gradle.util.TextUtil

import static org.gradle.util.TextUtil.normaliseLineSeparators

class ModelRuleBindingFailureIntegrationTest extends AbstractIntegrationSpec {

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
        failure.assertHasCause(unbound {
            rule(new SimpleModelRuleDescriptor('MyPlugin$Rules#thing1(MyPlugin$MyThing2)'))
                    .immutableUnbound(null, 'MyPlugin$MyThing2')
            rule(new SimpleModelRuleDescriptor('MyPlugin$Rules#mutateThing2(MyPlugin$MyThing2, MyPlugin$MyThing3)'))
                    .mutableUnbound(null, 'MyPlugin$MyThing2')
                    .immutableUnbound(null, 'MyPlugin$MyThing3')
        })
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
        failure.assertHasCause(unbound {
            rule(new SimpleModelRuleDescriptor('model.foo.bar'))
                    .mutableUnbound("foo.bar", 'java.lang.Object')
        })
    }

    String unbound(@DelegatesTo(UnboundRuleReportOutputBuilder) Closure<?> closure) {
        def string = new StringWriter()
        def writer = new PrintWriter(string)
        writer.println("The following model rules are unbound:")
        def builder = new UnboundRuleReportOutputBuilder(writer, "  ")
        builder.with(closure)
        normaliseLineSeparators(string.toString())
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
}
