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

package org.gradle.model.managed

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class ManagedModelMapIntegrationTest extends AbstractIntegrationSpec {

    def "rule can create a map of model elements"() {
        when:
        buildScript '''
            @Managed
            interface Thing extends Named {
              void setValue(String value)
              String getValue()
            }

            @Managed
            interface Container {
              ModelMap<Thing> getThings();
            }

            class Rules extends RuleSource {

              @Model
              void container(Container container) {
                container.things.create("a") { value = "1" }
                container.things.create("b") { value = "2" }
              }

              @Model
              void things(ModelMap<Thing> things) {
                things.create("a") { value = "1" }
                things.create("b") { value = "2" }
              }
            }

            apply type: Rules

            model {
              tasks {
                create("print") {
                  doLast {
                    println "containerThings: ${$.container.things.collect { it.name + ":" + it.value }.sort().join(",")}"
                    println "things: ${$.things.collect { it.name + ":" + it.value }.sort().join(",")}"
                  }
                }
              }
            }
        '''

        then:
        succeeds "print"

        and:
        output.contains "containerThings: a:1,b:2"
        output.contains "things: a:1,b:2"
    }

    def "rule can create a map of abstract class backed managed model elements"() {
        when:
        buildScript '''
            @Managed
            abstract class Thing implements Named {
              abstract String getName()
              abstract void setValue(String value)
              abstract String getValue()
            }

            @Managed
            interface Container {
              ModelMap<Thing> getThings();
            }

            class Rules extends RuleSource {

              @Model
              void container(Container container) {
                container.things.create("a") { value = "1" }
                container.things.create("b") { value = "2" }
              }

              @Model
              void things(ModelMap<Thing> things) {
                things.create("a") { value = "1" }
                things.create("b") { value = "2" }
              }
            }

            apply type: Rules

            model {
              tasks {
                create("print") {
                  doLast {
                    println "containerThings: ${$.container.things.collect { it.name + ":" + it.value }.sort().join(",")}"
                    println "things: ${$.things.collect { it.name + ":" + it.value }.sort().join(",")}"
                  }
                }
              }
            }
        '''

        then:
        succeeds "print"

        and:
        output.contains "containerThings: a:1,b:2"
        output.contains "things: a:1,b:2"
    }

    def "rule can create a map of various supported types"() {
        // TODO - can't actually add anything to these maps yet
        when:
        buildScript '''
            @Managed
            interface Thing extends Named {
              void setValue(String value)
              String getValue()
            }

            class Rules extends RuleSource {
              @Model
              void mapThings(ModelMap<ModelMap<Thing>> things) {
              }
              @Model
              void setThings(ModelMap<ModelSet<Thing>> things) {
              }
              @Model
              void setStrings(ModelMap<Set<String>> strings) {
              }
            }

            apply type: Rules

            model {
              mapThings {
              }
              setThings {
              }
              setStrings {
              }
              tasks {
                create("print") {
                  doLast {
                    println "mapThings: " + $.mapThings.keySet()
                    println "setThings: " + $.setThings.keySet()
                    println "setStrings: " + $.setStrings.keySet()
                  }
                }
              }
            }
        '''

        then:
        succeeds "print"

        and:
        output.contains "mapThings: []"
        output.contains "setThings: []"
        output.contains "setStrings: []"
    }

    def "fails when rule creates a map of unsupported type"() {
        when:
        buildScript '''
            @Managed
            interface Container {
              ModelMap<InputStream> getThings()
            }

            model {
              container(Container)
              tasks {
                create("print") {
                  doLast {
                    println $.container
                  }
                }
              }
            }
        '''

        then:
        fails "print"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: container(Container) @ build.gradle line 8, column 15")
        failure.assertHasCause("""A model element of type: 'Container' can not be constructed.
Its property 'org.gradle.model.ModelMap<java.io.InputStream> things' is not a valid managed collection
A managed collection can not contain 'java.io.InputStream's""")
    }

    def "reports failure that occurs in collection item initializer"() {
        when:
        buildScript '''
            @Managed
            interface Person extends Named {
              String getValue()
              void setValue(String string)
            }

            class Rules extends RuleSource {
              @Model
              void people(ModelMap<Person> people) {
                people.create("foo") {
                    throw new RuntimeException("broken")
                }
              }

              @Mutate
              void tasks(ModelMap<Task> tasks, ModelMap<Person> people) { }
            }

            apply type: Rules
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasDescription('A problem occurred configuring root project')
        failure.assertHasCause('Exception thrown while executing model rule: Rules#people(ModelMap<Person>) > create(foo)')
        failure.assertHasCause('broken')
    }

    def "cannot read when mutable"() {
        when:
        buildScript '''
            @Managed
            interface Person extends Named {
              String getValue()
              void setValue(String string)
            }

            class RulePlugin extends RuleSource {
                @Model
                void people(ModelMap<Person> people) {
                    people.size()
                }

                @Mutate
                void addDependencyOnPeople(ModelMap<Task> tasks, ModelMap<Person> people) {
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#people(ModelMap<Person>)")
        failure.assertHasCause("Attempt to read from a write only view of model element 'people' of type 'ModelMap<Person>' given to rule RulePlugin#people(ModelMap<Person>)")
    }

    def "cannot mutate when used as an input"() {
        when:
        buildScript '''
            @Managed
            interface Person extends Named {
              String getValue()
              void setValue(String string)
            }

            class RulePlugin extends RuleSource {
                @Model
                void people(ModelMap<Person> people) {}

                @Mutate
                void mutate(ModelMap<Task> tasks, ModelMap<Person> people) {
                    people.create("foo") {}
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails()

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#mutate(ModelMap<Task>, ModelMap<Person>)")
        failure.assertHasCause("Attempt to modify a read only view of model element 'people' of type 'ModelMap<Person>' given to rule RulePlugin#mutate(ModelMap<Task>, ModelMap<Person>)")
    }

    def "cannot mutate when used as subject of validate rule"() {
        when:
        buildScript '''
            @Managed
            interface Person extends Named {
              String getValue()
              void setValue(String string)
            }

            class RulePlugin extends RuleSource {
                @Model
                void people(ModelMap<Person> people) {
                }

                @Validate
                void check(ModelMap<Person> people) {
                    people.create("another")
                }

                @Mutate
                void mutate(ModelMap<Task> tasks, ModelMap<Person> people) {
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails()

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#check(ModelMap<Person>)")
        failure.assertHasCause("Attempt to modify a read only view of model element 'people' of type 'ModelMap<Person>' given to rule RulePlugin#check(ModelMap<Person>)")
    }

    def "can read children of map when used as input"() {
        when:
        buildScript """
            @Managed
            interface Parent {
                String getName();
                void setName(String string)

                ModelMap<Child> getChildren();
            }

            @Managed
            interface Child extends Named {
                ModelSet<GrandChild> getChildren();
            }

            @Managed
            interface GrandChild {
                String getName();
                void setName(String string)
            }

            class Rules extends RuleSource {
                @Model
                void parent(Parent p) {
                }

                @Mutate
                void printParentTask(TaskContainer tasks, Parent p) {
                    tasks.create("printParent") {
                        it.doLast {
                            println p.name
                            for (Child c : p.children.values()) {
                                println "  :" + c?.name
                                for (GrandChild gc : c.children) {
                                    println "    :" + gc?.name
                                }
                            }
                        }
                    }
                }

                @Mutate
                void addChildren(@Path("parent.children") children) {
                    children.create("c1") {
                        it.children.create { gc ->
                            gc.name = "gc1"
                        }
                    }
                }
            }

            apply type: Rules

            model {
                parent {
                    name = "parent"
                }
            }
        """

        then:
        succeeds "printParent"
        outputContains("""
parent
  :c1
    :gc1
""".trim()
        )
    }

    def "can read children of map when used as subject of validate rule"() {
        given:
        buildScript '''
            @Managed
            interface Person extends Named {
              String getValue()
              void setValue(String string)
            }

            class RulePlugin extends RuleSource {
                @Model
                void people(ModelMap<Person> people) {
                    people.create("a")
                }

                @Validate
                void check(ModelMap<Person> people) {
                    println "size: " + people.size()
                    println people.a
                    println people.keySet()
                }

                @Mutate
                void mutate(ModelMap<Task> tasks, ModelMap<Person> people) {
                }
            }

            apply type: RulePlugin
        '''

        when:
        run()

        then:
        output.contains("size: 1")
    }

    def "name is not populated when entity is not named"() {
        when:
        buildScript '''
            @Managed
            interface Thing {
              String getName()
              void setName(String name)
            }

            class Rules extends RuleSource {
              @Model
              void things(ModelMap<Thing> things) {
                things.create("a")
                things.create("b")
              }
            }

            apply type: Rules

            model {
              tasks {
                create("print") {
                  doLast {
                    println "things: ${$.things.collect { it.name }.join(',')}"
                  }
                }
              }
            }
        '''

        then:
        succeeds "print"

        and:
        output.contains "things: null,null"
    }

}
