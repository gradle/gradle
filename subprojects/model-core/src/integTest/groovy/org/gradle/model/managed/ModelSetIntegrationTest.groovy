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

package org.gradle.model.managed

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class ModelSetIntegrationTest extends AbstractIntegrationSpec {

    def "provides basic meta-data for set"() {
        when:
        buildScript '''
            @Managed
            interface Person {
            }

            class Rules extends RuleSource {
              @Model
              void people(ModelSet<Person> people) {
              }
            }

            apply type: Rules

            model {
              tasks {
                create("printPeople") {
                  doLast {
                    def people = $.people
                    println "name: $people.name"
                    println "display-name: $people.displayName"
                    println "to-string: ${people.toString()}"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printPeople"

        and:
        output.contains "name: people"
        output.contains "display-name: ModelSet<Person> 'people'"
        output.contains "to-string: ModelSet<Person> 'people'"
    }

    def "can view as ModelElement"() {
        when:
        buildScript '''
            @Managed
            interface Person {
            }

            class Rules extends RuleSource {
              @Model
              void people(ModelSet<Person> people) {
              }

              @Mutate
              void tasks(ModelMap<Task> tasks, @Path("people") ModelElement people) {
                tasks.create("printPeople") {
                  doLast {
                    println "name: $people.name"
                    println "display-name: $people.displayName"
                    println "to-string: ${people.toString()}"
                  }
                }
              }
            }

            apply type: Rules
        '''

        then:
        succeeds "printPeople"

        and:
        output.contains "name: people"
        output.contains "display-name: ModelSet<Person> 'people'"
        output.contains "to-string: ModelSet<Person> 'people'"
    }

    def "rule can create a managed collection of interface backed managed model elements"() {
        when:
        buildScript '''
            @Managed
            interface Person {
              String getName()
              void setName(String string)
            }

            class Names {
                List<String> names = []
            }

            class Rules extends RuleSource {
              @Model
              Names names() {
                return new Names(names: ["p1", "p2"])
              }

              @Model
              void people(ModelSet<Person> people, Names names) {
                names.names.each { n ->
                    people.create { name = n }
                }
              }

              @Mutate void addPeople(ModelSet<Person> people) {
                people.create { name = "p3" }
                people.create { name = "p4" }
              }
            }

            apply type: Rules

            model {
              people {
                create { name = "p0" }
              }

              tasks {
                create("printPeople") {
                  doLast {
                    def people = $.people
                    def names = people*.name.sort().join(", ")
                    println "names: $names"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printPeople"

        and:
        output.contains 'names: p0, p1, p2, p3, p4'
    }

    def "rule can create a managed collection of abstract class backed managed model elements"() {
        when:
        buildScript '''
            @Managed
            abstract class Person {
              abstract String getName()
              abstract void setName(String string)
            }

            class Rules extends RuleSource {
              @Model
              void people(ModelSet<Person> people) {
                people.create { name = "p1" }
                people.create { name = "p2" }
              }
            }

            apply type: Rules

            model {
              tasks {
                def people = $.people
                create("printPeople") {
                  doLast {
                    def names = people*.name.sort().join(", ")
                    println "people: $names"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printPeople"

        and:
        output.contains 'people: p1, p2'
    }

    def "rule can create a set of various supported types"() {
        when:
        buildScript '''
            @Managed
            interface Thing extends Named {
              void setValue(String value)
              String getValue()
            }

            class Rules extends RuleSource {
              @Model
              void mapThings(ModelSet<ModelMap<Thing>> things) {
                things.create {
                    a(Thing) {
                        value = '1'
                    }
                    b(Thing)
                }
              }
              @Model
              void setThings(ModelSet<ModelSet<Thing>> things) {
                things.create {
                    create { value = '1' }
                }
              }
              @Model
              void setStrings(ModelSet<Set<String>> strings) {
                strings.create {
                    add 'a'
                }
              }
            }

            apply type: Rules

            model {
              mapThings {
                create {
                    a(Thing)
                }
              }
              setStrings {
                create {
                    add 'b'
                }
              }
              tasks {
                create("print") {
                  doLast {
                    println "mapThings: " + ($.mapThings as List)*.keySet()
                    println "setThings: " + ($.setThings as List)
                    println "setStrings: " + ($.setStrings as List)
                  }
                }
              }
            }
        '''

        then:
        succeeds "print"

        and:
        output.contains "mapThings: [[a, b], [a]]"
        output.contains "setThings: [[Thing 'setThings.0.0']]"
        output.contains "setStrings: [[a], [b]]"
    }

    def "managed model type has property of collection of managed types"() {
        when:
        buildScript '''
            @Managed
            interface Person {
              String getName()
              void setName(String string)
            }

            @Managed
            interface Group {
              String getName()
              void setName(String string)
              ModelSet<Person> getMembers()
            }

            class Rules extends RuleSource {
              @Model
              void group(Group group) {
                group.name = "Women in computing"

                group.members.create { name = "Ada Lovelace" }
                group.members.create { name = "Grace Hooper" }

                assert group.members.is(group.members)
              }
            }

            apply type: Rules

            model {
              tasks {
                def g = $.group
                create("printGroup") {
                  doLast {
                    def members = g.members*.name.sort().join(", ")
                    def name = g.name
                    println "$name: $members"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printGroup"

        and:
        output.contains 'Women in computing: Ada Lovelace, Grace Hooper'
    }

    def "managed model cannot have a reference to a model set"() {
        when:
        buildScript '''
            @Managed
            interface Person {
              String getName()
              void setName(String string)
            }

            @Managed
            interface Group {
              String getName()
              void setName(String string)
              ModelSet<Person> getMembers()
              //Invalid setter
              void setMembers(ModelSet<Person> members)
            }

            class Rules extends RuleSource {
              @Model
              void group(Group group, @Path("people") ModelSet<Person> people) {
              }
            }

            apply type: Rules
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause "Exception thrown while executing model rule: Rules#group"
        failure.assertHasCause """Type Group is not a valid managed type:
- Property 'members' is not valid: it cannot have a setter (ModelSet properties must be read only)"""
    }

    def "rule method can apply defaults to a managed set"() {
        when:
        buildScript '''
            @Managed
            interface Person {
              String getName()
              void setName(String string)
            }

            class Rules extends RuleSource {
              @Model
              void people(ModelSet<Person> people) {
                println "initialize"
              }

              @Defaults void initialPeople(ModelSet<Person> people) {
                println "apply defaults"
              }

              @Mutate void customPeople(ModelSet<Person> people) {
                println "configure"
              }

              @Finalize void finalPeople(ModelSet<Person> people) {
                println "finalize"
              }
            }

            apply type: Rules

            model {
              tasks {
                create("printPeople") {
                  doLast {
                    def people = $.people
                    println "people: $people"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printPeople"

        and:
        output.contains '''apply defaults
initialize
configure
finalize
'''
    }

    def "creation and configuration of managed set elements is deferred until required"() {
        when:
        buildScript '''
            @Managed
            abstract class Person {
              Person() {
                println "construct Person"
              }
              abstract String getName()
              abstract void setName(String string)
            }

            class Rules extends RuleSource {
              @Model
              void people(ModelSet<Person> people) {
                people.create {
                    println "configure p1"
                    name = "p1"
                }
                println "p1 defined"
              }

              @Mutate void addPeople(ModelSet<Person> people) {
                people.create {
                  println "configure p2"
                  name = "p2"
                }
                println "p2 defined"
              }
            }

            apply type: Rules

            model {
              people {
                create {
                  println "configure p3"
                  name = "p3"
                }
                println "p3 defined"
              }

              tasks {
                create("printPeople") {
                  doLast {
                    def names = $.people*.name.sort().join(", ")
                    println "people: $names"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printPeople"

        and:
        output.contains '''p1 defined
p2 defined
p3 defined
construct Person
configure p1
construct Person
configure p2
construct Person
configure p3
'''

        output.contains "people: p1, p2, p3"
    }

    def "reports failure that occurs in collection item initializer"() {
        when:
        buildScript '''
            @Managed
            interface Person {
              String getName()
              void setName(String string)
            }

            class Rules extends RuleSource {
              @Model
              void people(ModelSet<Person> people) {
                people.create {
                    throw new RuntimeException("broken")
                }
              }

              @Mutate
              void tasks(ModelMap<Task> tasks, ModelSet<Person> people) { }
            }

            apply type: Rules
        '''

        then:
        fails "printPeople"

        and:
        failure.assertHasDescription('A problem occurred configuring root project')
        failure.assertHasCause('Exception thrown while executing model rule: Rules#people')
        failure.assertHasCause('broken')
    }

    def "read methods of ModelSet throw exceptions when used in a creation rule"() {
        when:
        buildScript '''
            @Managed
            interface Person {
            }

            class RulePlugin extends RuleSource {
                @Model
                void people(ModelSet<Person> people) {
                    people.size()
                }

                @Mutate
                void addDependencyOnPeople(ModelMap<Task> tasks, ModelSet<Person> people) {
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#people(ModelSet<Person>)")
        failure.assertHasCause("Attempt to read from a write only view of model element 'people' of type 'ModelSet<Person>' given to rule RulePlugin#people(ModelSet<Person>)")
    }

    def "read methods of ModelSet throw exceptions when used in a mutation rule"() {
        when:
        buildScript '''
            @Managed
            interface Person {
            }

            class RulePlugin extends RuleSource {
                @Model
                void people(ModelSet<Person> people) {
                }

                @Mutate
                void readPeople(ModelSet<Person> people) {
                    people.toList()
                }

                @Mutate
                void addDependencyOnPeople(ModelMap<Task> tasks, ModelSet<Person> people) {
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#readPeople(ModelSet<Person>)")
        failure.assertHasCause("Attempt to read from a write only view of model element 'people' of type 'ModelSet<Person>' given to rule RulePlugin#readPeople(ModelSet<Person>)")
    }

    def "mutating a managed set that is an input of a rule is not allowed"() {
        when:
        buildScript '''
            @Managed
            interface Person {
            }

            class RulePlugin extends RuleSource {
                @Model
                void people(ModelSet<Person> people) {}

                @Mutate
                void tryToMutateInputModelSet(ModelMap<Task> tasks, ModelSet<Person> people) {
                    people.create {}
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#tryToMutateInputModelSet(ModelMap<Task>, ModelSet<Person>)")
        failure.assertHasCause("Attempt to modify a read only view of model element 'people' of type 'ModelSet<Person>' given to rule RulePlugin#tryToMutateInputModelSet(ModelMap<Task>, ModelSet<Person>)")
    }

    def "mutating a managed set that is the subject of a validation rule is not allowed"() {
        when:
        buildScript '''
            @Managed
            interface Person {
            }

            class RulePlugin extends RuleSource {
                @Model
                void people(ModelSet<Person> people) {}

                @Validate
                void check(ModelSet<Person> people) {
                    people.create { }
                }

                @Mutate
                void tryToMutateInputModelSet(ModelMap<Task> tasks, ModelSet<Person> people) {
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#check(ModelSet<Person>)")
        failure.assertHasCause("Attempt to modify a read only view of model element 'people' of type 'ModelSet<Person>' given to rule RulePlugin#check(ModelSet<Person>)")
    }

    def "mutating a managed set outside of a creation rule is not allowed"() {
        when:
        buildScript '''
            @Managed
            interface Person {
            }

            class Holder {
                static ModelSet<Person> people
            }

            class RulePlugin extends RuleSource {
                @Model
                void people(ModelSet<Person> people) {
                    Holder.people = people
                }

                @Mutate
                void tryToMutateModelSetOutsideOfCreationRule(ModelMap<Task> tasks, ModelSet<Person> people) {
                    Holder.people.create {}
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#tryToMutateModelSetOutsideOfCreationRule(ModelMap<Task>, ModelSet<Person>)")
        failure.assertHasCause("Attempt to modify a closed view of model element 'people' of type 'ModelSet<Person>' given to rule RulePlugin#people(ModelSet<Person>)")
    }

    def "mutating managed set which is an input of a DSL rule is not allowed"() {
        when:
        buildScript '''
            @Managed
            interface Person {
            }

            class RulePlugin extends RuleSource {
                @Model
                void people(ModelSet<Person> people) {
                }
            }

            apply type: RulePlugin

            model {
                tasks {
                    $.people.create {}
                }
            }
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: tasks { ... } @ build.gradle")
        failure.assertHasCause("Attempt to modify a read only view of model element 'people' of type 'ModelSet<Person>' given to rule tasks { ... } @ build.gradle")
    }
}
