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

package org.gradle.launcher.continuous

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest

class BuildSrcContinuousIntegrationTest extends AbstractContinuousIntegrationTest {

    def setup() {
        file("buildSrc/src/main/groovy/Thing.groovy") << """
            class Thing {
              public static final String VALUE = "original"
            }
        """

        // Trigger generation of Gradle JARs before executing any test case
        succeeds("help")
    }

    def "can build and reload a project with buildSrc when buildSrc changes"() {
        when:
        buildScript """
            task a {
              inputs.files "a"
              doLast {
                println "value: " + Thing.VALUE
              }
            }
        """

        then:
        succeeds("a")
        outputContains "value: original"

        when:
        file("buildSrc/src/main/groovy/Thing.groovy").text = """
            class Thing {
              public static final String VALUE = "changed"
            }
        """

        then:
        buildTriggeredAndSucceeded()
        outputContains "value: changed"
    }

}
