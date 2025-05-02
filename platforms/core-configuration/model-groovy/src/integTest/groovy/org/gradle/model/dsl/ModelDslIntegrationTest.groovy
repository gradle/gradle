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

package org.gradle.model.dsl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

/**
 * Tests the fundamental usages of the model dsl.
 *
 * Boundary tests for the transform and specialised cases should go in other dedicated test classes.
 */
@UnsupportedWithConfigurationCache(because = "software model")
class ModelDslIntegrationTest extends AbstractIntegrationSpec {

    def "can reference rule inputs using dollar method syntax"() {
        when:
        buildFile '''
            class MyPlugin extends RuleSource {
                @Model
                String foo() {
                  "foo"
                }

                @Model
                List<String> strings() {
                  []
                }
            }

            apply plugin: MyPlugin

            model {
                tasks {
                    def strings = $('strings')
                    printStrings(Task) {
                        doLast {
                            println "strings: " + strings
                        }
                    }
                }
                strings {
                    add($('foo'))
                }
            }
'''

        then:
        succeeds "printStrings"
        output.contains "strings: [foo]"
    }

    def "rule inputs can be referenced in closures that are not executed during rule execution"() {
        when:
        buildFile '''
            class MyPlugin extends RuleSource {
                @Model
                String foo() {
                  "foo"
                }

                @Model
                List<String> strings() {
                  []
                }
            }

            apply type: MyPlugin

            model {
              tasks {
                create("printStrings") {
                  doLast {
                    // Being in doLast is significant here.
                    // This is not going to execute until much later, so we are testing that we can still access the input
                    println "strings: " + $.strings
                  }
                }
              }
              strings {
                add $.foo
              }
            }
        '''

        then:
        succeeds "printStrings"
        output.contains "strings: " + ["foo"]
    }

    def "inputs are fully configured when used in rules"() {
        when:
        buildFile '''
            class MyPlugin extends RuleSource {
                @Model
                List<String> strings() {
                  []
                }
            }

            apply type: MyPlugin

            model {
              tasks {
                create("printStrings") {
                  doLast {
                    println "strings: " + $.strings
                  }
                }
              }
              strings {
                add "foo"
              }
              strings {
                add "bar"
              }
            }
        '''

        then:
        succeeds "printStrings"
        output.contains "strings: " + ["foo", "bar"]
    }

    def "the same input can be referenced more than once, and refers to the same object"() {
        when:
        buildFile '''
            class MyPlugin extends RuleSource {
                @Model
                List<String> strings() {
                  []
                }
            }

            apply type: MyPlugin

            model {
              tasks {
                create("assertDuplicateInputIsSameObject") {
                  doLast {
                    assert $("strings").is($("strings"))
                    def s = $.strings
                    assert $.strings.is($.strings)
                    assert s == $.strings
                    assert $.strings.is($("strings"))
                    this.with {
                        // Nested in a closure
                        assert $.strings.is(s)
                    }
                  }
                }
              }
            }
        '''

        then:
        succeeds "assertDuplicateInputIsSameObject"
    }

    def "reports on the first reference to unknown input"() {
        when:
        buildFile '''
            model {
              tasks {
                $.unknown
                $("unknown")
              }
            }
        '''

        then:
        fails "tasks"
        failure.assertHasCause('''The following model rules could not be applied due to unbound inputs and/or subjects:

  tasks { ... } @ build.gradle line 3, column 15
    subject:
      - tasks Object
    inputs:
      - unknown Object (@ line 4) [*]

''')
    }

    def "reports on configuration action failure"() {
        when:
        buildFile '''
            model {
              tasks {
                unknown = 12
              }
            }
        '''

        then:
        fails "tasks"
        failure.assertHasCause('Exception thrown while executing model rule: tasks { ... } @ build.gradle line 3, column 15')
    }

    def "can use model block in script plugin"() {
        given:
        settingsFile << "include 'a'; include 'b'"
        when:

        buildFile '''
            class MyPlugin extends RuleSource {
                @Model
                String foo() {
                  "foo"
                }

                @Model
                List<String> strings() {
                  []
                }
            }

            subprojects {
                apply type: MyPlugin
                apply from: "$rootDir/script.gradle"
            }
        '''
        file("a/build.gradle") << """
            model {
              strings { add "a" }
            }
        """
        file("b/build.gradle") << """
            model {
              strings { add "b" }
            }
        """
        file("script.gradle") << '''
            model {
              tasks {
                create("printStrings") {
                  def projectName = it.project.name
                  doLast {
                    println projectName + ": " + $.strings
                  }
                }
              }
              strings {
                add $.foo
              }
            }
        '''

        then:
        succeeds "printStrings"
        output.contains "a: " + ["foo", "a"]
        output.contains "b: " + ["foo", "b"]
    }
}
