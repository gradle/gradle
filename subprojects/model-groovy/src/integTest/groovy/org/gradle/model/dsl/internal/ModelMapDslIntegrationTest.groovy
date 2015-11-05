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

package org.gradle.model.dsl.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ModelMapDslIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << '''
@Managed
interface Thing {
    String getValue()
    void setValue(String v)
}

class MyPlugin extends RuleSource {
    @Model
    void things(ModelMap<Thing> things) { }
}

apply plugin: MyPlugin
'''
    }

    def "nested create rule is executed only as required"() {
        buildFile << '''
model {
    tasks {
        show(Task) {
            doLast {
                println "value = " + $.things.test.value
            }
        }
    }
    broken(Thing) {
        throw new RuntimeException('broken')
    }
    things {
        main(Thing) {
            value = $.broken.value
        }
        test(Thing) {
            value = "12"
        }
    }
}
'''
        when:
        succeeds "show"

        then:
        result.output.contains("value = 12")
    }

    def "nested configure rule is executed only as required"() {
        buildFile << '''
model {
    tasks {
        show(Task) {
            doLast {
                println "value = " + $.things.test.value
            }
        }
    }
    broken(Thing) {
        throw new RuntimeException('broken')
    }
    things {
        main {
            value = $.broken.value
        }
        test {
            value = "12"
        }
        main(Thing)
        test(Thing)
    }
}
'''
        when:
        succeeds "show"

        then:
        result.output.contains("value = 12")
    }

    def "nested create rule can reference sibling as input"() {
        buildFile << '''
model {
    tasks {
        show(Task) {
            doLast {
                println "value = " + $.things.test.value
            }
        }
    }
    things {
        main(Thing)
        test(Thing) {
            println "configure test"
            value = $.things.main.value
        }
    }
    things {
        main {
            println "configure main"
            value = "12"
        }
    }
}
'''
        when:
        succeeds "show"

        then:
        result.output.contains('''configure main
configure test
''')
        result.output.contains("value = 12")
    }

    def "nested configure rule can reference sibling as input"() {
        buildFile << '''
model {
    tasks {
        show(Task) {
            doLast {
                println "value = " + $.things.test.value
            }
        }
    }
    things {
        main(Thing)
        test(Thing)
    }
    things {
        test {
            println "configure test"
            value = $.things.main.value
        }
        main {
            println "configure main"
            value = "12"
        }
    }
}
'''
        when:
        succeeds "show"

        then:
        result.output.contains('''configure main
configure test
''')
        result.output.contains("value = 12")
    }

    def "reports failure in initialization action"() {
        buildFile << '''
model {
    things {
        main(Thing) {
            unknown = 12
        }
    }
}
'''

        expect:
        fails 'model'
        failure.assertHasCause('Exception thrown while executing model rule: main(Thing) { ... } @ build.gradle line 17, column 9')
        failure.assertHasCause('No such property: unknown for class: Thing')
    }

    def "reports failure in configuration action"() {
        buildFile << '''
model {
    things {
        main {
            unknown = 12
        }
        main(Thing)
    }
}
'''

        expect:
        fails 'model'
        failure.assertHasCause('Exception thrown while executing model rule: main { ... } @ build.gradle line 17, column 9')
        failure.assertHasCause('No such property: unknown for class: Thing')
    }
}
