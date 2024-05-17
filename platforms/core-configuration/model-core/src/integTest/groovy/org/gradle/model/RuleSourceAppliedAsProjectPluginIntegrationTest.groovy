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
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class RuleSourceAppliedAsProjectPluginIntegrationTest extends AbstractIntegrationSpec {

    def "plugin class can expose model rules"() {
        when:
        buildScript '''
            class MyPlugin {
                static class Rules extends RuleSource {
                    @Model
                    List<String> strings() {
                      []
                    }

                    @Mutate
                    void addTasks(ModelMap<Task> tasks, List<String> strings) {
                        tasks.create("value") {
                            it.doLast {
                                println "value: $strings"
                            }
                        }
                    }

                }
            }

            apply type: MyPlugin

            model {
                strings {
                    add "foo"
                }
            }
        '''

        then:
        succeeds "value"

        and:
        output.contains "value: [foo]"
    }

    def "plugin RuleSource can be abstract"() {
        buildFile << '''
@Managed
interface Thing {
    String getName()
    void setName(String name)
}

abstract class MyPlugin extends RuleSource {
    @Model
    void p1(Thing t) {
        println "creating " + t + " from " + toString()
        assert this == this
        t.name = 'name'
    }
}

apply plugin: MyPlugin

model {
    tasks {
        show(Task) {
            doLast {
                println "p1 = " + $.p1.name
            }
        }
    }
}
'''

        when:
        run 'show'

        then:
        output.contains("creating Thing 'p1' from rule source MyPlugin")
        output.contains("p1 = name")
    }

    def "configuration in script is not executed if not needed"() {
        given:
        buildScript '''
            class MyPlugin {
                static class Rules extends RuleSource {
                    @Model
                    List<String> strings() {
                      []
                    }
                }
            }

            apply type: MyPlugin

            model {
                strings {
                    throw new RuntimeException();
                }
            }

            task value
        '''

        expect:
        succeeds "value"
    }

    def "informative error message when rules are invalid"() {
        when:
        buildScript """
            class MyPlugin {
                class Rules extends RuleSource {
                }
            }

            apply type: MyPlugin
        """

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Failed to apply plugin class 'MyPlugin'")
        failure.assertHasCause('''Type MyPlugin.Rules is not a valid rule source:
- Enclosed classes must be static and non private
- Cannot declare a constructor that takes arguments''')
    }

    def "informative error message when two plugins declare model at the same path"() {
        when:
        buildScript """
            class MyPlugin {
                static class Rules extends RuleSource {
                    @Model
                    String string() { "foo" }
                }
            }

            class MyOtherPlugin {
                static class Rules extends RuleSource {
                    @Model
                    String string() { "foo" }
                }
            }

            apply type: MyPlugin
            apply type: MyOtherPlugin
        """

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Failed to apply plugin class 'MyOtherPlugin'")
        failure.assertHasCause("Cannot create 'string' using creation rule 'MyOtherPlugin.Rules#string()' as the rule 'MyPlugin.Rules#string()' is already registered to create this model element.")
    }

    def "informative error message when two plugins declare model at the same path and model is already created"() {
        when:
        buildScript '''
            class MyPlugin {
                static class Rules extends RuleSource {
                    @Model
                    String string() { "foo" }
                }
            }

            class MyOtherPlugin {
                static class Rules extends RuleSource {
                    @Model
                    String string() { "bar" }
                }
            }

            apply type: MyPlugin

            model {
                tasks {
                    $.string
                }
            }

            task loadPlugin {
                doLast {
                    apply type: MyOtherPlugin
                }
            }
        '''

        then:
        fails "loadPlugin"

        and:
        failure.assertHasCause("Failed to apply plugin class 'MyOtherPlugin'")
        failure.assertHasCause("Cannot create 'string' using creation rule 'MyOtherPlugin.Rules#string()' as the rule 'MyPlugin.Rules#string()' has already been used to create this model element.")
    }

    def "informative error message when creation rule throws"() {
        when:
        buildScript '''
            class MyPlugin {
                static class Rules extends RuleSource {
                    @Model
                    String string() { throw new RuntimeException("oh no!") }
                }
            }

            apply type: MyPlugin

            model {
                tasks {
                    $.string
                }
            }
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin.Rules#string")
        failure.assertHasCause("oh no!")
    }

    def "informative error message when mutation rule throws"() {
        when:
        buildScript '''
            class MyPlugin {
                static class Rules extends RuleSource {
                    @Model
                    String string() { "foo" }

                    @Mutate
                    void broken(String s) {
                        throw new RuntimeException("oh no!")
                    }
                }
            }

            apply type: MyPlugin

            model {
                tasks {
                    $.string
                }
            }
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin.Rules#broken")
        failure.assertHasCause("oh no!")
    }

    def "model registration must provide instance"() {
        when:
        buildScript '''
            class MyPlugin {
                static class Rules extends RuleSource {
                    @Model
                    String string() {
                      null
                    }
                }
            }

            apply type: MyPlugin

            model {
                tasks {
                    $.string
                }
            }
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("error executing model rule: MyPlugin.Rules#string() - rule returned null")
    }

    def "plugin applied by plugin can contribute rules"() {
        when:
        buildScript '''
            class MyBasePlugin {
                static class Rules extends RuleSource {
                    @Mutate
                    void strings(List<String> strings) {
                      strings << "foo"
                    }
                }
            }

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.pluginManager.apply(MyBasePlugin)
                }

                static class Rules extends RuleSource {
                    @Model
                    List<String> strings() {
                      []
                    }

                    @Mutate
                    void addTasks(ModelMap<Task> tasks, List<String> strings) {
                        tasks.create("value") {
                            it.doLast {
                                println "value: $strings"
                            }
                        }
                    }
                }
            }

            apply plugin: MyPlugin
        '''

        then:
        succeeds "value"

        and:
        output.contains "value: [foo]"
    }

    def "configuration made to a project extension during afterEvaluate() is visible to rule sources"() {
        when:
        buildScript '''
            class MyExtension {
                String value = "original"
            }

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.extensions.create("myExtension", MyExtension)
                }

                static class Rules extends RuleSource {
                    @Model
                    MyExtension myExtension(ExtensionContainer extensions) {
                        extensions.getByType(MyExtension)
                    }

                    @Model
                    String value(MyExtension myExtension) {
                        myExtension.value
                    }

                    @Mutate
                    void addTasks(ModelMap<Task> tasks, String value) {
                        tasks.create("value") {
                            it.doLast {
                                println "value: $value"
                            }
                        }
                    }
                }
            }

            apply plugin: MyPlugin

            project.afterEvaluate {
                project.myExtension.value = "configured"
            }
        '''

        then:
        succeeds "value"

        and:
        output.contains "value: configured"
    }

    def "rule can depend on a concrete task type"() {
        when:
        buildScript '''
            class MyPlugin {
                static class Rules extends RuleSource {
                    @Mutate
                    void addTasks(ModelMap<Task> tasks, @Path("tasks.injected") Exec execTask) {
                        tasks.create("name") {
                            it.doLast {
                                println "name: ${execTask.name}"
                            }
                        }
                    }
                }
            }

            apply type: MyPlugin

            task injected(type: Exec)
        '''

        then:
        succeeds "name"

        and:
        output.contains "name: injected"
    }

    def "reports rule execution failure when rule source constructor throws exception"() {
        when:
        buildScript '''
            class Rules extends RuleSource {
                Rules() {
                    throw new RuntimeException("failing constructor")
                }
                @Defaults
                void tasks(ModelMap<Task> tasks) {
                }
            }

            apply type: Rules
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: Rules#tasks")
        failure.assertHasCause("failing constructor")
    }
}
