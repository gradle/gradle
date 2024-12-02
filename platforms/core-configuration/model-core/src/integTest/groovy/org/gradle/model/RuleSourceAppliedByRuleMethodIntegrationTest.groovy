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

package org.gradle.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.StableConfigurationCacheDeprecations
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.language.base.LanguageSourceSet

@UnsupportedWithConfigurationCache(because = "software model")
class RuleSourceAppliedByRuleMethodIntegrationTest extends AbstractIntegrationSpec implements StableConfigurationCacheDeprecations {
    def "@Rule method can apply rules to a particular target"() {
        buildFile << '''
            @Managed
            interface Thing {
                String getName()
                void setName(String name)
            }

            class MyPlugin extends RuleSource {
                @Model
                void p1(Thing t) {
                    assert t.name == 'default'
                    t.name = 'p1'
                }

                @Model
                void p2(Thing t) {
                    assert t.name == null
                    t.name = 'p2'
                }

                @Rules
                void rules(CalculateName rules, @Path('p1') Thing t) {
                    println "applying rules to $t"
                    assert rules != null
                    assert t != null
                }
            }

            class CalculateName extends RuleSource {
                @Defaults
                void defaultName(Thing t) {
                    t.name = 'default'
                }

                @Finalize
                void finalizeName(Thing t) {
                    assert t.name == 'p1'
                    t.name = 'p1 finalized'
                }
            }

            apply plugin: MyPlugin

            model {
                tasks {
                    show(Task) {
                        doLast {
                            println "p1 = " + $.p1.name
                            println "p2 = " + $.p2.name
                        }
                    }
                }
            }
        '''

        when:
        run 'show'

        then:
        output.contains("applying rules to Thing 'p1'")
        output.contains("p1 = p1 finalized")
        output.contains("p2 = p2")
    }

    def "@Rule method can apply rules to target of current rule source"() {
        buildFile << '''
            @Managed
            interface Thing {
                String getName()
                void setName(String name)
            }

            class MyPlugin extends RuleSource {
                @Model
                void p1(Thing t) {
                }

                @Rules
                void rules(CalculateName rules, Thing t) {
                }
            }

            class CalculateName extends RuleSource {
                @Rules
                void rules(SpecializeName rules, Thing t) {
                }
            }

            class SpecializeName extends RuleSource {
                @Defaults
                void defaultName(Thing t) {
                    t.name = 'default'
                }

                @Finalize
                void finalizeName(Thing t) {
                    assert t.name == 'default'
                    t.name = 'finalized'
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
        output.contains("p1 = finalized")
    }

    def "@Rule method can apply abstract RuleSource"() {
        buildFile << '''
            @Managed
            interface Thing {
                String getName()
                void setName(String name)
            }

            class MyPlugin extends RuleSource {
                @Model
                void p1(Thing t) {
                }

                @Rules
                void rules(CalculateName rules, Thing t) {
                    println "applying " + rules + " to " + t
                    assert rules == rules
                }
            }

            abstract class CalculateName extends RuleSource {
                @Defaults
                void defaultName(Thing t) {
                    println "applying defaults from " + toString()
                    assert this == this
                    t.name = 'default'
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
        output.contains("applying rule source CalculateName to Thing 'p1'")
        output.contains("applying defaults from rule source CalculateName")
        output.contains("p1 = default")
    }

    def "@Rule method can apply abstract RuleSource with scalar input properties"() {
        buildFile << '''
            @Managed
            interface Thing {
                String getName()
                void setName(String name)
            }

            class MyPlugin extends RuleSource {
                @Model
                void p1(Thing t) {
                }

                @Rules
                void rules(CalculateName rules, Thing t) {
                    assert rules.defaultName == null
                    rules.defaultName = 'default'
                    assert rules.defaultName == 'default'
                }
            }

            abstract class CalculateName extends RuleSource {
                @RuleInput
                abstract String getDefaultName()
                abstract void setDefaultName(String n)

                @Defaults
                void defaultName(Thing t) {
                    assert defaultName == 'default'
                    t.name = defaultName
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
        output.contains("p1 = default")
    }

    def "elements referenced by @RuleInput property are treated as implicit input of rules on RuleSource"() {
        buildFile << '''
            @Managed
            interface SomeThings {
                Thing getThingA()
                Thing getThingB()
                Thing getThingC()
            }

            @Managed
            interface Thing {
                String getName()
                void setName(String name)
            }

            class MyPlugin extends RuleSource {
                @Model
                void things(SomeThings t) {
                }

                @Rules
                void rules(CalculateName rules, SomeThings t) {
                    assert rules.thing == null
                    rules.thing = t.thingA
                    assert rules.thing == t.thingA
                }
            }

            abstract class CalculateName extends RuleSource {
                @RuleInput
                abstract Thing getThing()
                abstract void setThing(Thing t)

                @Defaults
                void defaultB(@Path('thingB') Thing thingB) {
                    assert thing.name == 'thing a from dsl'
                    thingB.name = thing.name
                }

                @Finalize
                void defaultC(@Path('thingC') Thing thingC) {
                    assert thing.name == 'thing a from dsl'
                    thingC.name = 'thing c from rule'
                }
            }

            apply plugin: MyPlugin

            model {
                things.thingA {
                    name = 'thing a from dsl'
                }
                things.thingB {
                    assert name == 'thing a from dsl'
                    name = 'thing b from dsl'
                }
                tasks {
                    show(Task) {
                        doLast {
                            def things = $.things
                            println "thingA = " + things.thingA.name
                            println "thingB = " + things.thingB.name
                            println "thingC = " + things.thingC.name
                        }
                    }
                }
            }
        '''

        when:
        run 'show'

        then:
        output.contains("thingA = thing a from dsl")
        output.contains("thingB = thing b from dsl")
        output.contains("thingC = thing c from rule")
    }

    def "element referenced by @RuleTarget property is treated as target of RuleSource"() {
        buildFile << '''
            @Managed
            interface SomeThings {
                Thing getThingA()
                Thing getThingB()
            }

            @Managed
            interface Thing {
                String getName()
                void setName(String name)
                Address getAddress()
            }

            @Managed
            interface Address {
                String getName()
                void setName(String name)
            }

            class MyPlugin extends RuleSource {
                @Model
                void things(SomeThings t) {
                }

                @Rules
                void rules(CalculateName rules, SomeThings t) {
                    assert rules.thing == null
                    rules.thing = t.thingA
                    assert rules.thing == t.thingA
                }
            }

            abstract class CalculateName extends RuleSource {
                @RuleTarget
                abstract Thing getThing()
                abstract void setThing(Thing t)

                @Mutate
                void thing(Thing subject) {
                    assert subject == thing
                    subject.name = 'thing a from rule'
                    subject.address.name = 'address a from rule'
                }

                @Mutate
                void address(Address address) {
                    assert address == thing.address
                    assert address.name == 'address a from rule'
                    address.name = 'modified address'
                }
            }

            apply plugin: MyPlugin

            model {
                tasks {
                    show(Task) {
                        doLast {
                            def things = $.things
                            println "thingA = " + things.thingA.name
                            println "thingA.address = " + things.thingA.address.name
                        }
                    }
                }
            }
        '''

        when:
        run 'show'

        then:
        output.contains("thingA = thing a from rule")
        output.contains("thingA.address = modified address")
    }

    def "reports exception thrown by @Rules method"() {
        buildFile << '''
            class MyPlugin extends RuleSource {
                @Model
                void strings(List<String> s) {}

                @Rules
                void rules(OtherRules rules, List<String> s) {
                    throw new RuntimeException("broken")
                }
            }

            class OtherRules extends RuleSource {
            }

            apply plugin: MyPlugin
        '''

        expect:
        expectTaskGetProjectDeprecations()
        fails("model")
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin#rules")
        failure.assertHasCause("broken")
    }

    def "reports exception thrown by rule method on applied RuleSource"() {
        buildFile << '''
            @Managed
            interface Thing {
                String getName()
                void setName(String name)
            }

            class MyPlugin extends RuleSource {
                @Model
                void p1(Thing t) {
                }

                @Rules
                void rules(CalculateName rules, Thing t) {
                }
            }

            class CalculateName extends RuleSource {
                @Defaults
                void defaultName(Thing t) {
                    throw new RuntimeException("broken")
                }
            }

            apply plugin: MyPlugin
        '''

        expect:
        expectTaskGetProjectDeprecations()
        fails 'model'
        failure.assertHasCause("Exception thrown while executing model rule: CalculateName#defaultName")
        failure.assertHasCause("broken")
    }

    def "reports exception thrown by rule source constructor"() {
        buildFile << '''
            @Managed
            interface Thing {
                String getName()
                void setName(String name)
            }

            class MyPlugin extends RuleSource {
                @Model
                void p1(Thing t) {
                }

                @Rules
                void rules(CalculateName rules, Thing t) {
                }
            }

            class CalculateName extends RuleSource {
                CalculateName() {
                    throw new RuntimeException("broken")
                }
            }

            apply plugin: MyPlugin
        '''

        expect:
        expectTaskGetProjectDeprecations()
        fails 'model'
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin#rules")
        failure.assertHasCause("broken")
    }

    def "@Rules method is not executed when target is not required"() {
        buildFile << '''
            class MyPlugin extends RuleSource {
                @Model
                void strings(List<String> s) {}

                @Rules
                void rules(RuleSource rules, List<String> s) {
                    throw new RuntimeException("broken")
                }
            }

            apply plugin: MyPlugin
        '''

        expect:
        succeeds("tasks")
        expectTaskGetProjectDeprecations()
        fails("model")
    }

    def "first parameter of @Rules method must be assignable to RuleSource"() {
        buildFile << '''
            class MyPlugin extends RuleSource {
                @Rules
                void rules(LanguageSourceSet lss, String string) {
                }
            }
            apply plugin: MyPlugin
        '''

        expect:
        fails 'model'
        failure.assertHasCause("""Type MyPlugin is not a valid rule source:
- Method rules(${LanguageSourceSet.name}, ${String.name}) is not a valid rule method: The first parameter of a method annotated with @Rules must be a subtype of ${RuleSource.name}""")
    }

    def "reports declaration problem with applied RuleSource"() {
        buildFile << '''
            class MyPlugin extends RuleSource {
                @Model
                void strings(List<String> s) {}

                @Rules
                void rules(BrokenRuleSource rules, List<String> s) {
                }
            }

            class BrokenRuleSource extends RuleSource {
                @Validate
                private void broken() { }
            }

            apply plugin: MyPlugin
        '''

        expect:
        expectTaskGetProjectDeprecations()
        fails("model")
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin#rules")
        failure.assertHasCause('''Type BrokenRuleSource is not a valid rule source:
- Method broken() is not a valid rule method: A rule method cannot be private
- Method broken() is not a valid rule method: A method annotated with @Validate must have at least one parameter''')
    }

    def "reports unbound parameters for rules on applied RuleSource"() {
        buildFile '''
            class UnboundRuleSource extends RuleSource {
                @Mutate
                void unboundRule(String string, Integer integer, @Path("some.inner.path") String withPath) {
                }
            }

            class MyPlugin extends RuleSource {
                @Model
                void strings(List<String> s) {}

                @Rules
                void rules(UnboundRuleSource rules, List<String> s) {
                }
            }

            apply type: MyPlugin
        '''

        expect:
        expectTaskGetProjectDeprecations()
        fails "model"
        failure.assertHasCause('''The following model rules could not be applied due to unbound inputs and/or subjects:

  UnboundRuleSource#unboundRule(String, Integer, String)
    subject:
      - <no path> String (parameter 1) [*]
          scope: strings
    inputs:
      - <no path> Integer (parameter 2) [*]
      - strings.some.inner.path String (parameter 3) [*]
''')
    }

    def "cannot mutate implicit input of RuleSource method"() {
        buildFile << '''
            @Managed
            interface SomeThings {
                Thing getThingA()
            }

            @Managed
            interface Thing {
                String getName()
                void setName(String name)
            }

            class MyPlugin extends RuleSource {
                @Model
                void things(SomeThings t) {
                }

                @Model
                void calculated(Thing t) {
                }

                @Rules
                void rules(CalculateName rules, Thing t, SomeThings others) {
                    rules.thing = others.thingA
                }
            }

            abstract class CalculateName extends RuleSource {
                @RuleInput
                abstract Thing getThing()
                abstract void setThing(Thing t)

                @Mutate
                void broken(Thing t) {
                    println "check"
                    assert thing.name == 'thing a'
                    thing.name = 'broken'
                }
            }

            apply plugin: MyPlugin

            model {
                things.thingA {
                    name = 'thing a'
                }
                tasks {
                    show(Task) {
                        doLast {
                            println $.calculated
                        }
                    }
                }
            }
        '''

        when:
        fails 'show'

        then:
        failure.assertHasCause("Exception thrown while executing model rule: CalculateName#broken(Thing)")
        failure.assertHasCause("Attempt to modify a read only view of model element 'things.thingA' of type 'Thing' given to rule CalculateName#broken(Thing)")
    }

}
