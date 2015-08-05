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
import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.util.TextUtil

class ModelSetIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        EnableModelDsl.enable(executer)
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
                    def people = $("people")
                    def names = people*.name.sort().join(", ")
                    println "people: ${people.toString()}"
                    println "names: $names"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printPeople"

        and:
        output.contains "people: org.gradle.model.ModelSet<Person> 'people'"
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
                create("printPeople") {
                  doLast {
                    def names = $("people")*.name.sort().join(", ")
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
                create("printGroup") {
                  doLast {
                    def members = $("group").members*.name.sort().join(", ")
                    def name = $("group").name
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
        failure.assertHasCause("Declaration of model rule Rules#group is invalid.")
        failure.assertHasCause("Invalid managed model type Group: property 'members' cannot have a setter (org.gradle.model.ModelSet<Person> properties must be read only)")
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
                    def people = $("people")
                    println "people: $people"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printPeople"

        and:
        output.contains TextUtil.toPlatformLineSeparators('''apply defaults
initialize
configure
finalize
''')
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
                    def names = $("people")*.name.sort().join(", ")
                    println "people: $names"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printPeople"

        and:
        output.contains TextUtil.toPlatformLineSeparators('''
p1 defined
p2 defined
p3 defined
construct Person
configure p1
construct Person
configure p2
construct Person
configure p3
''')

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
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#people")
        failure.assertHasCause("Attempt to read a write only view of model of type 'org.gradle.model.ModelSet<Person>' given to rule 'RulePlugin#people'")
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
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#readPeople")
        failure.assertHasCause("Attempt to read a write only view of model of type 'org.gradle.model.ModelSet<Person>' given to rule 'RulePlugin#readPeople'")
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
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#tryToMutateInputModelSet")
        failure.assertHasCause("Attempt to mutate closed view of model of type 'org.gradle.model.ModelSet<Person>' given to rule 'RulePlugin#tryToMutateInputModelSet'")
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
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#tryToMutateModelSetOutsideOfCreationRule")
        failure.assertHasCause("Attempt to mutate closed view of model of type 'org.gradle.model.ModelSet<Person>' given to rule 'RulePlugin#people'")
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
                    $("people").create {}
                }
            }
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: model.tasks")
        failure.assertHasCause("Attempt to mutate closed view of model of type 'org.gradle.model.ModelSet<Person>' given to rule 'model.tasks @ build.gradle")
    }
}
