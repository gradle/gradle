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
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class ConfigurationCycleIntegrationTest extends AbstractIntegrationSpec {

    def "configuration cycle error contains information useful for troubleshooting"() {
        when:
        buildScript '''
            class Rules extends RuleSource {
                @Model
                String first(@Path("second") String second) {
                    "foo"
                }

                @Model
                String second() {
                    "bar"
                }

                @Model
                String third(@Path("first") String first) {
                    "fizz"
                }

                @Mutate
                void connectTasksToFirst(ModelMap<Task> tasks, @Path("first") String first) {
                }
            }

            apply type: Rules

            model {
                second {
                    $.third
                }
            }
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("""A cycle has been detected in model rule dependencies. References forming the cycle:
first
\\- Rules#first(String)
   \\- second
      \\- second { ... } @ build.gradle line 26, column 17
         \\- third
            \\- Rules#third(String)
               \\- first""")
    }

    def "cycles involving multiple rules of same phase are detected"() {
        when:
        buildScript '''
            class Rules extends RuleSource {
                @Model List<String> m1() { [] }
                @Model List<String> m2() { [] }
                @Model List<String> m3() { [] }

                @Mutate void m2ToM1(@Path("m1") m1, @Path("m2") m2) {
                    if (!m1.empty) {
                        throw new IllegalStateException("m2ToM1 has executed twice")
                    }
                    m1 << "executed"
                }

                // in cycle…
                @Mutate void m3ToM1(@Path("m1") m1, @Path("m3") m3) {}
                @Mutate void m1ToM3(@Path("m3") m3, @Path("m1") m1) {}

                @Mutate void addTask(ModelMap<Task> tasks, @Path("m1") m1) {}
            }

            apply type: Rules
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("""A cycle has been detected in model rule dependencies. References forming the cycle:
m1
\\- Rules#m3ToM1(Object, Object)
   \\- m3
      \\- Rules#m1ToM3(Object, Object)
         \\- m1""")
    }

    def "cycles involving multiple rules of different phase are detected"() {
        when:
        buildScript '''
            class Rules extends RuleSource {
                @Model List<String> m1() { [] }
                @Model List<String> m2() { [] }
                @Model List<String> m3() { [] }

                @Defaults void addM1Defaults(@Path("m1") m1) {
                    if (!m1.empty) {
                        throw new IllegalStateException("addM1Defaults has executed twice")
                    }
                    m1 << "addM3Defaults executed"
                }

                @Mutate void m2ToM1(@Path("m1") m1, @Path("m2") m2) {
                    if (m1.size() > 1) {
                        throw new IllegalStateException("m2ToM1 has executed twice")
                    }
                    m1 << "m2ToM1 executed"
                }


                // in cycle…
                @Mutate void m3ToM1(@Path("m1") m1, @Path("m3") m3) {}
                @Mutate void m1ToM3(@Path("m3") m3, @Path("m1") m1) {}

                @Mutate void addTask(ModelMap<Task> tasks, @Path("m1") m1) {}
            }

            apply type: Rules
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("""A cycle has been detected in model rule dependencies. References forming the cycle:
m1
\\- Rules#m3ToM1(Object, Object)
   \\- m3
      \\- Rules#m1ToM3(Object, Object)
         \\- m1""")

    }
}
