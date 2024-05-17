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

package org.gradle.model.dsl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

@UnsupportedWithConfigurationCache(because = "software model")
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
        outputContains("value = 12")
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
        outputContains("value = 12")
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
        outputContains('''configure main
configure test
''')
        outputContains("value = 12")
    }

    def "nested create rule defined using ModelMap API methods can reference sibling as input"() {
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
        create('test') {
            println "configure test"
            value = $.things.main.value
        }
        create('main')
    }
    things {
        named('main') {
            println "configure main"
            value = "12"
        }
    }
}
'''
        when:
        succeeds "show"

        then:
        outputContains('''configure main
configure test
''')
        outputContains("value = 12")
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
        outputContains('''configure main
configure test
''')
        outputContains("value = 12")
    }

    def "nested configure rule defined using ModelMap API methods can reference sibling as input"() {
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
        named('test') {
            println "configure test"
            value = $.things.main.value
        }
        named('main') {
            println "configure main"
            value = "12"
        }
    }
}
'''
        when:
        succeeds "show"

        then:
        outputContains('''configure main
configure test
''')
        outputContains("value = 12")
    }

    @Requires(UnitTestPreconditions.IsGroovy3)
    def "nested rule cannot reference method of delegate of outer closure with Groovy 3"() {
        buildFile << '''
model {
    things {
        create('main') {
            create('test') { println "no" }
        }
    }
}
'''
        expect:
        fails "model"
        failure.assertHasLineNumber(18)
        failure.assertHasCause('Exception thrown while executing model rule: create(main) { ... } @ build.gradle line 17, column 9')
        failure.assertHasCause('No signature of method: org.gradle.api.Project.create() is applicable for argument types:')
    }

    @Requires(UnitTestPreconditions.IsGroovy4)
    def "nested rule cannot reference method of delegate of outer closure with Groovy 4"() {
        buildFile << '''
model {
    things {
        create('main') {
            create('test') { println "no" }
        }
    }
}
'''
        expect:
        fails "model"
        failure.assertHasLineNumber(18)
        failure.assertHasCause('Exception thrown while executing model rule: create(main) { ... } @ build.gradle line 17, column 9')
        failure.assertHasCause('No signature of method: Thing.create() is applicable for argument types')
    }

    def "nested rule can reference vars defined in outer closure"() {
        buildFile << '''
model {
    things {
        def a = 12
        main(Thing) {
            value = a
        }
    }
}
'''
        expect:
        succeeds "model"
    }

    // This is temporary. Will be closed once more progress on DSL has been made
    def "nested rule can reference methods of project and script"() {
        buildFile << '''
def a = 12
model {
    things {
        main(Thing) {
            a = files('a')
            a == 9
            project.tasks
        }
    }
}
'''
        expect:
        succeeds "model"
    }

    def "can create and configure elements dynamically"() {
        buildFile << '''
model {
    tasks {
        show(Task) {
            doLast {
                println "value = " + $.things*.value
            }
        }
    }
    things {
        main(Thing) {
            value = 'foo'
        }
        for (name in ['a', 'b', 'c']) {
            def n = name
            "$name"(Thing) {
                value = "$n:${$.things.main.value}"
            }
            "$name" {
                value = "[$value]"
            }
        }
    }
}
'''
        when:
        succeeds "show"

        then:
        outputContains("value = [[a:foo], [b:foo], [c:foo], foo]")
    }

    def "can create and configure elements conditionally"() {
        buildFile << '''
model {
    tasks {
        show(Task) {
            doLast {
                println "value = " + $.things*.value
            }
        }
    }
    things {
        main(Thing) {
            value = 'foo'
        }
        if (true) {
            test(Thing) {
                value = $.things.main.value
            }
        }
        if ("true") {
            test {
                value = "[test:$value]"
            }
        }
    }
}
'''
        when:
        succeeds "show"

        then:
        outputContains("value = [foo, [test:foo]]")
    }

    def "reports nested rule location for failure in initialization action"() {
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

    def "reports nested rule location for failure in configuration action"() {
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

    def "reports nested rule location for failure in all() configuration action"() {
        buildFile << '''
model {
    things {
        all {
            unknown = 12
        }
        main(Thing)
    }
}
'''

        expect:
        fails 'model'
        failure.assertHasCause('Exception thrown while executing model rule: all { ... } @ build.gradle line 17, column 9')
        failure.assertHasCause('No such property: unknown for class: Thing')
    }

    def "reports nested rule location for failure in withType() configuration action"() {
        buildFile << '''
model {
    things {
        withType(Thing) {
            unknown = 12
        }
        main(Thing)
    }
}
'''

        expect:
        fails 'model'
        failure.assertHasCause('Exception thrown while executing model rule: withType(Thing) { ... } @ build.gradle line 17, column 9')
        failure.assertHasCause('No such property: unknown for class: Thing')
    }

    def "reports nested rule location for failure in defaults action"() {
        buildFile << '''
model {
    things {
        beforeEach(Thing) {
            unknown = 12
        }
        main(Thing)
    }
}
'''

        expect:
        fails 'model'
        failure.assertHasCause('Exception thrown while executing model rule: beforeEach(Thing) { ... } @ build.gradle line 17, column 9')
        failure.assertHasCause('No such property: unknown for class: Thing')
    }

    def "reports nested rule location for failure in finalize action"() {
        buildFile << '''
model {
    things {
        afterEach(Thing) {
            unknown = 12
        }
        main(Thing)
    }
}
'''

        expect:
        fails 'model'
        failure.assertHasCause('Exception thrown while executing model rule: afterEach(Thing) { ... } @ build.gradle line 17, column 9')
        failure.assertHasCause('No such property: unknown for class: Thing')
    }
}
