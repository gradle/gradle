/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ListPropertyIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            class MyTask extends DefaultTask {
                ListProperty<String> prop = project.objects.listProperty(String)
                List<String> expected = []
                
                @TaskAction
                void validate() {
                    def actual = prop.getOrNull()
                    println 'Actual: ' + actual
                    println 'Expected: ' + expected
                    assert actual == expected
                }
            }
            
            task verify(type: MyTask)
        """
    }

    def "can set value for list property from DSL"() {
        buildFile << """
            verify {
                prop = ${value}
                expected = [ 'a', 'b', 'c' ]
            }
        """
        expect:
        succeeds("verify")

        where:
        value                                      | _
        "[ 'a', 'b', 'c' ]"                        | _
        "new LinkedHashSet([ 'a', 'b', 'c' ])"     | _
        "providers.provider { [ 'a', 'b', 'c' ] }" | _
    }

    def "can add to default values"() {
        buildFile << """
            verify {
                prop = [ 'a' ]
                prop.add('b')
                prop.add(project.provider { 'c' })
                prop.addAll(project.provider { [ 'd', 'e' ] })
                expected = [ 'a', 'b', 'c', 'd', 'e' ]
            }
        """
        expect:
        succeeds("verify")
    }

    def "can add to empty list property"() {
        buildFile << """
            verify {
                prop.add('a')
                prop.add(project.provider { 'b' })
                prop.addAll(project.provider { [ 'c', 'd' ] })
                expected = [ 'a', 'b', 'c', 'd' ]
            }
        """
        expect:
        succeeds("verify")
    }

    def "adds to non-defined property do nothing"() {
        buildFile << """
            verify {
                prop = null
                prop.add('b')
                prop.add(project.provider { 'c' })
                prop.addAll(project.provider { [ 'd', 'e' ] })
                expected = null
            }
        """
        expect:
        succeeds("verify")
    }

    def "reasonable message when trying to add a null to a list property"() {
        buildFile << """
            verify {
                prop.add(null)
            }
        """
        expect:
        def failure = fails("verify")
        failure.assertHasCause("Cannot add a null element to a property of type List.")
    }

    def "has no value when providing null to a list property"() {
        buildFile << """
            verify {
                prop.add(project.provider { null })
                expected = null
            }
        """
        expect:
        succeeds("verify")
    }

    def "has no value when providing null list to a list property"() {
        buildFile << """
            verify {
                prop.addAll(project.provider { null })
                expected = null
            }
        """
        expect:
        succeeds("verify")
    }
}
