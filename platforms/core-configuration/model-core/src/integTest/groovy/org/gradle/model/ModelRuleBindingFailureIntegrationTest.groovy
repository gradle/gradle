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

/**
 * Tests the information provided when a model rule fails to bind.
 *
 * @see ModelRuleBindingValidationIntegrationTest
 */
@UnsupportedWithConfigurationCache(because = "software model")
class ModelRuleBindingFailureIntegrationTest extends AbstractIntegrationSpec {

    def "unbound rule by-type subject and inputs are reported"() {
        given:
        buildScript """
            class MyPlugin {
                static class MyThing1 {}
                static class MyThing2 {}
                static class MyThing3 {}

                static class Rules extends RuleSource {
                    @Model
                    MyThing1 thing1(MyThing2 thing2) {
                        new MyThing1()
                    }

                    @Mutate
                    void mutateThing2(MyThing2 thing2, MyThing3 thing3n, String someOtherThing, Integer intParam) {
                    }

                    @Defaults
                    void subjectOnly(MyThing2 thing2) {
                    }
                }
            }

            apply type: MyPlugin
        """

        when:
        fails "tasks"

        then:
        failureCauseContains '''
  MyPlugin.Rules#mutateThing2(MyPlugin.MyThing2, MyPlugin.MyThing3, String, Integer)
    subject:
      - <no path> MyPlugin.MyThing2 (parameter 1) [*]
    inputs:
      - <no path> MyPlugin.MyThing3 (parameter 2) [*]
      - <no path> String (parameter 3) [*]
      - <no path> Integer (parameter 4) [*]

  MyPlugin.Rules#subjectOnly(MyPlugin.MyThing2)
    subject:
      - <no path> MyPlugin.MyThing2 (parameter 1) [*]

  MyPlugin.Rules#thing1(MyPlugin.MyThing2)
    inputs:
      - <no path> MyPlugin.MyThing2 (parameter 1) [*]
'''
    }

    def "unbound rule by-path subject and inputs are reported"() {
        given:
        buildScript """
            class MyPlugin {
                static class MyThing1 {}
                static class MyThing2 {}
                static class MyThing3 {}

                static class Rules extends RuleSource {
                    @Model
                    MyThing1 thing1(@Path("foo.bar.baz") MyThing2 thing2) {
                        new MyThing1()
                    }

                    @Mutate
                    void mutateThing2(@Path("foo") MyThing2 thing2, @Path("foo.bar") MyThing3 thing3n, String someOtherThing, Integer intParam) {
                    }
                }
            }

            apply type: MyPlugin
        """

        when:
        fails "tasks"

        then:
        failureCauseContains '''
  MyPlugin.Rules#mutateThing2(MyPlugin.MyThing2, MyPlugin.MyThing3, String, Integer)
    subject:
      - foo MyPlugin.MyThing2 (parameter 1) [*]
    inputs:
      - foo.bar MyPlugin.MyThing3 (parameter 2) [*]
      - <no path> String (parameter 3) [*]
      - <no path> Integer (parameter 4) [*]

  MyPlugin.Rules#thing1(MyPlugin.MyThing2)
    inputs:
      - foo.bar.baz MyPlugin.MyThing2 (parameter 1) [*]
'''
    }

    def "unbound dsl rule by-path subject and inputs are reported"() {
        given:
        buildScript '''
            @Managed interface Thing { }

            model {
                foo.bar {
                    // Subject only
                }
                foo.bla {
                    println $.unknown.thing
                    println $.unknown.thing2
                }
                thing1(Thing) {
                    println $.unknown.thing
                }
            }
        '''

        when:
        fails "tasks"

        then:
        // TODO - should report unknown inputs as well
        failureCauseContains """
  foo.bar { ... } @ build.gradle line 5, column 17
    subject:
      - foo.bar Object [*]

  foo.bla { ... } @ build.gradle line 8, column 17
    subject:
      - foo.bla Object [*]
"""
    }

    def "suggestions are provided for unbound by-path references"() {
        given:
        buildScript """
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
                tasks.foonar {
                }
            }
        """

        when:
        fails "tasks"

        then:
        failureCauseContains '''
  tasks.foonar { ... } @ build.gradle line 15, column 17
    subject:
      - tasks.foonar Object [*]
          suggestions: tasks.foobar
'''
    }

    def "fails on ambiguous by-type reference"() {
        given:
        buildScript """
            class Plugin1 {
                static class Rules extends RuleSource {
                    @Model
                    String s1() {
                        "foo"
                    }
                }
            }

            class Plugin2 {
                static class Rules extends RuleSource {
                    @Model
                    String s2() {
                        "bar"
                    }
                }
            }

            class Plugin3 {
                static class Rules extends RuleSource {
                    @Mutate
                    void m(String s) {
                        "foo"
                    }
                }
            }

            apply type: Plugin1
            apply type: Plugin2
            apply type: Plugin3
        """

        when:
        fails "tasks"

        then:
        failure.assertHasDescription("A problem occurred configuring root project")
        failure.assertHasCause("There is a problem with model rule Plugin3.Rules#m(String).")
        failure.assertHasCause("""Type-only model reference of type java.lang.String (parameter 1) is ambiguous as multiple model elements are available for this type:
  - s1 (created by: Plugin1.Rules#s1())
  - s2 (created by: Plugin2.Rules#s2())""")
    }

    def "fails on incompatible by-type reference"() {
        given:
        buildScript """
            class Plugin1 {
                static class Rules extends RuleSource {
                    @Mutate
                    void addTasks(@Path("tasks") Integer s1) {

                    }
                }
            }

            apply type: Plugin1
        """

        when:
        fails "tasks"

        then:
        failure.assertHasCause("There is a problem with model rule Plugin1.Rules#addTasks(Integer).")
        failure.assertHasCause("""Model reference to element 'tasks' with type java.lang.Integer (parameter 1) is invalid due to incompatible types.
This element was created by Project.<init>.tasks() and can be mutated as the following types:
  - org.gradle.model.ModelMap<org.gradle.api.Task>
  - org.gradle.api.tasks.TaskContainer (or assignment compatible type thereof)""")
    }

    def "reports failure to bind subject or input due to null reference"() {
        given:
        buildScript """
@Managed interface Person extends Named {
    Person getParent()
    void setParent(Person p)
}

class MyPlugin extends RuleSource {
    @Model
    void person(Person p) { }

    @Model
    String name(@Path("person.parent.parent") Person grandParent, @Path("person.parent.parent.parent.parent") Person ancestor) {
        throw new RuntimeException("broken")
    }

    @Validate
    void checkName(@Path("person.parent.parent") Person grandParent, @Path("person.parent.parent.parent.parent") Person ancestor) {
        throw new RuntimeException("broken")
    }
}

apply plugin: MyPlugin

model {
    person.parent.name {
        throw new RuntimeException("broken")
    }
    person.parent.parent.parent.parent.parent.name {
        throw new RuntimeException("broken")
    }
}
"""

        when:
        fails()

        then:
        failureCauseContains """
  MyPlugin#checkName(Person, Person)
    subject:
      - person.parent.parent Person (parameter 1) [*]
    inputs:
      - person.parent.parent.parent.parent Person (parameter 2) [*]

  MyPlugin#name(Person, Person)
    inputs:
      - person.parent.parent Person (parameter 1) [*]
      - person.parent.parent.parent.parent Person (parameter 2) [*]

  person.parent.name { ... } @ build.gradle line 25, column 5
    subject:
      - person.parent.name Object [*]

  person.parent.parent.parent.parent.parent.name { ... } @ build.gradle line 28, column 5
    subject:
      - person.parent.parent.parent.parent.parent.name Object [*]
"""
    }

    def "partially bound rules are reported and the report includes the elements bound to"() {
        given:
        buildScript """
            class MyPlugin {
                static class MyThing1 {}
                static class MyThing2 {}
                static class MyThing3 {}

                static class Rules extends RuleSource {
                    @Model
                    MyThing1 thing1() {
                        new MyThing1()
                    }

                    @Mutate
                    void thing1(MyThing1 t1, MyThing3 t3) {
                    }

                    @Mutate
                    void mutateThing2(MyThing2 t2, MyThing1 t1) {
                    }
                }
            }

            apply type: MyPlugin
        """

        when:
        fails "tasks"

        then:
        failureCauseContains '''
  MyPlugin.Rules#mutateThing2(MyPlugin.MyThing2, MyPlugin.MyThing1)
    subject:
      - <no path> MyPlugin.MyThing2 (parameter 1) [*]
    inputs:
      - thing1 MyPlugin.MyThing1 (parameter 2)

  MyPlugin.Rules#thing1(MyPlugin.MyThing1, MyPlugin.MyThing3)
    subject:
      - thing1 MyPlugin.MyThing1 (parameter 1)
    inputs:
      - <no path> MyPlugin.MyThing3 (parameter 2) [*]
'''
    }
}
