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
class EnumsInManagedModelIntegrationTest extends AbstractIntegrationSpec {

    def "can use enums in managed model elements"() {
        when:
        buildScript '''
            enum Gender {
                FEMALE, MALE, OTHER
            }

            @Managed
            interface Person {
              String getName()
              void setName(String string)

              Gender getGender()
              void setGender(Gender gender)
            }

            class Rules extends RuleSource {
              @Model
              void p1(Person p1) {}
            }

            apply type: Rules

            model {
              p1 {
                gender = "MALE" // relying on Groovy enum coercion here
              }

              tasks {
                create("printGender") {
                  it.doLast {
                    println "gender: " + $("p1").gender
                  }
                }
              }
            }
        '''

        then:
        succeeds "printGender"

        and:
        output.contains 'gender: MALE'
    }
}
