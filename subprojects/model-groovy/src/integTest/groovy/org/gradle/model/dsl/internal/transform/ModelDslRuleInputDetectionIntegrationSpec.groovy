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

class ModelDslRuleInputDetectionIntegrationSpec extends AbstractIntegrationSpec {

    @Unroll
    def "can reference input using dollar method expression - #syntax"() {
        when:
        buildScript """
          @Managed
          interface Thing {
            String getValue(); void setValue(String str)
          }

          model {
            thing(Thing) {
                value = "foo"
            }
            tasks {
                def v = $syntax
                create("echo") {
                    doLast {
                        println "thing.value: " + v
                    }
                }
            }
          }
        """

        then:
        succeeds "echo"
        output.contains "thing.value: foo"

        where:
        syntax << [
            '$("thing.value")',
            '$("thing").value',
            '$("thing").getValue()',
            "(\$('thing')).value",
            "(\$('thing')).(value)",
            '$("thing")."${"value"}"',
            "\$('thing').'value'",
            "\$('thing').value.toUpperCase().toLowerCase()",
            "\$('thing').value[0] + 'oo'",
            "(true ? \$('thing.value') : 'bar')",
            "new String(\$('thing.value'))",
        ]
    }

    @Unroll
    def "can reference input using dollar var expression - #syntax"() {
        when:
        buildScript """
          @Managed
          interface Thing {
            String getValue(); void setValue(String str)
          }

          model {
            thing(Thing) {
                value = "foo"
            }
            tasks {
                def v = $syntax
                create("echo") {
                    doLast {
                        println "thing.value: " + v
                    }
                }
            }
          }
        """

        then:
        succeeds "echo"
        output.contains "thing.value: foo"

        where:
        syntax << [
            '$.thing.value',
            '$."thing"."value"',
            '$.thing.getValue()',
            "\$.'thing'.'value'",
            '$.thing.value.toUpperCase().toLowerCase()',
            '(true ? $.thing.value : "bar")',
            "new String(\$.thing.value)",
        ]
    }

    @Unroll
    def "can inject input as parameter of rule closure - #syntax"() {
        when:
        buildScript """
          @Managed
          interface Thing {
            String getValue(); void setValue(String str)
          }

          model {
            thing(Thing) {
                value = "foo"
            }
            tasks { t, v = $syntax ->
                create("echo") {
                    doLast {
                        println "thing.value: " + v
                    }
                }
            }
          }
        """

        then:
        succeeds "echo"
        output.contains "thing.value: foo"

        where:
        syntax << [
            '$("thing.value")',
            '$("thing").value',
            '$("thing.value").toString()',
            '$.thing.value'
        ]
    }

    @Unroll
    def "input reference can be used as expression statement - #syntax"() {
        when:
        buildScript """
          @Managed
          interface Thing {
            String getValue(); void setValue(String str)
          }

          model {
            tasks {
                $syntax
                println "tasks configured"
            }
            thing(Thing) {
                println "thing configured"
            }
          }
        """

        then:
        succeeds "tasks"
        outputContains '''thing configured
tasks configured
'''

        where:
        syntax << [
            '$.thing.value',
            '$("thing.value")'
        ]
    }

    @Unroll
    def "dollar var must be followed by property expression - #code"() {
        when:
        buildScript """
        model {
          foo {
            $code
          }
        }
        """

        then:
        fails "tasks"
        failure.assertHasLineNumber 4
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertHasDescription("Could not compile build file '${buildFile}'")
        failure.assertThatCause(containsString('Invalid variable name. Must include a letter but only found: $'))

        where:
        code << [
            '$',
            '$.toString()',
            '$.$("foo")',
            '$."${ 1 + 2}"',
            '$*.things',
            '$?.things',
        ]
    }

    def "path for dollar var expression ends with first non-property reference"() {
        when:
        buildScript '''
        @Managed
        interface Thing {
            List<String> getValues()
        }
        model {
            thing(Thing) {
                values.addAll(['', '2', '3'])
            }
            tasks {
                echo(Task) {
                    doLast {
                        println "values: " + $.thing.values*.empty
                        println "values: " + $.thing.values?.empty
                    }
                }
            }
        }
        '''

        then:
        succeeds "echo"
        outputContains "values: [true, false, false]"
    }

    @Unroll
    def "only literal strings can be given to dollar method - #code"() {
        when:
        buildScript """
        model {
          foo {
            $code
          }
        }
        """

        then:
        fails "tasks"
        failure.assertHasLineNumber 4
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertThatCause(containsString(RuleVisitor.INVALID_ARGUMENT_LIST))

        where:
        code << [
            '$(1)',
            '$("$name")',
            '$("a" + "b")',
            'def a = "foo"; $(a)',
            '$("foo", "bar")',
            '$()',
            '$(null)',
            '$("")'
        ]
    }

    @Unroll
    def "dollar method is only detected with no explicit receiver - #code"() {
        when:
        buildScript """
            class MyPlugin {
              static class Rules extends RuleSource {
                @Model
                String foo() {
                  "foo"
                }
              }
            }

            apply type: MyPlugin

            model {
              foo {
                $code
              }
            }
        """

        then:
        succeeds "tasks" // succeeds because we don't fail on invalid usage, and don't fail due to unbound inputs

        where:
        code << [
            'something.$(1)',
            'this.$("$name")',
            'foo.bar().$("a" + "b")',
        ]
    }

    @Unroll
    def "dollar var is only detected with no explicit receiver - #code"() {
        when:
        buildScript """
            class MyPlugin {
              static class Rules extends RuleSource {
                @Model
                String foo() {
                  "foo"
                }
              }
            }

            apply type: MyPlugin

            model {
              foo {
                $code
              }
            }
        """

        then:
        succeeds "tasks" // succeeds because we don't fail on invalid usage, and don't fail due to unbound inputs

        where:
        code << [
            'something.$.foo',
            'this.$.foo',
            'foo.bar().$.foo'
        ]
    }

    @Unroll
    def "input references are found in nested code - #code"() {
        when:
        buildScript """
            class MyPlugin {
                static class Rules extends RuleSource {
                    @Mutate void addPrintTask(ModelMap<Task> tasks, List<String> strings) {
                        tasks.create("printMessage", PrintTask) {
                            it.message = strings
                        }
                    }

                    @Model String foo() {
                        "foo"
                    }

                    @Model List<String> strings() {
                        []
                    }
                }
            }

            class PrintTask extends DefaultTask {
                String message

                @TaskAction
                void doPrint() {
                    println "message: " + message
                }
            }

            apply type: MyPlugin

            model {
                strings {
                    $code
                }
            }
        """

        then:
        succeeds "printMessage"
        outputContains("message: [foo]")

        where:
        code << [
            'if (true) { add $.foo }',
            'if (true) { add $("foo") }',
            'if (false) {} else if (true) { add $("foo") }',
            'if (false) {} else { add $("foo") }',
            'def i = true; while(i) { add $("foo"); i = false }',
            '[1].each { add $("foo") }',
            'add "${$("foo")}"',
            'def v = $("foo"); add(v)',
            'add($("foo"))',
            'add($("foo").toString())',
            '''
def cl = { foo = $("foo") -> add(foo) }
cl.call()
''',
            '{ foo = true ? $("foo") : "bar" -> add(foo) }.call()',
            '{ foo = true ? $.foo : "bar" -> add(foo) }.call()'
        ]
    }

    def "input model path must be valid"() {
        when:
        buildScript """
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
              tasks {
                \$("foo. bar") // line 21
              }
            }
        """

        then:
        fails "tasks"
        failure.assertHasLineNumber(15)
        failure.assertThatCause(containsString("Invalid model path given as rule input."))
        failure.assertThatCause(containsString("Model path 'foo. bar' is invalid due to invalid name component."))
        failure.assertThatCause(containsString("Model element name ' bar' has illegal first character ' ' (names must start with an ASCII letter or underscore)."))
    }

    def "location and suggestions are provided for unbound rule subject specified using a name"() {
        given:
        buildScript '''
            class MyPlugin extends RuleSource {
                @Model
                String foobar() {
                    return "value"
                }
                @Model
                String raboof() {
                    return "value"
                }
            }

            apply type: MyPlugin

            model {
                foonar {
                }
                foobah {
                }
                fooar {
                }
            }
        '''

        when:
        fails "tasks"

        then:
        failureCauseContains('''
  fooar { ... } @ build.gradle line 20, column 17
    subject:
      - fooar Object [*]
          suggestions: foobar

  foobah { ... } @ build.gradle line 18, column 17
    subject:
      - foobah Object [*]
          suggestions: foobar

  foonar { ... } @ build.gradle line 16, column 17
    subject:
      - foonar Object [*]
          suggestions: foobar
''')
    }

    def "location and suggestions are provided for unbound rule inputs specified using a name"() {
        given:
        buildScript '''
            class MyPlugin {
                static class Rules extends RuleSource {
                    @Mutate
                    void addTasks(ModelMap<Task> tasks) {
                        tasks.create("foobar")
                        tasks.create("raboof")
                    }
                }
            }

            apply type: MyPlugin

            model {
                tasks.raboof {
                    $('tasks.foonar')
                    $('tasks.fooar')
                    $.tasks.foobarr
                }
            }
        '''

        when:
        fails "tasks"

        then:
        failureCauseContains('''
  tasks.raboof { ... } @ build.gradle line 15, column 17
    subject:
      - tasks.raboof Object
    inputs:
      - tasks.foonar Object (@ line 16) [*]
          suggestions: tasks.foobar
      - tasks.fooar Object (@ line 17) [*]
          suggestions: tasks.foobar
      - tasks.foobarr Object (@ line 18) [*]
          suggestions: tasks.foobar
''')
    }

    // This is temporary. Will be closed once more progress on DSL has been made
    def "can access project and script from rule"() {
        when:
        buildScript """
            def a = '12'
            model {
                tasks {
                    project.tasks
                    println project.tasks
                    files('thing')
                    a = files('thing')
                    a = '9'
                }
            }
        """

        then:
        succeeds "tasks"
    }

}
