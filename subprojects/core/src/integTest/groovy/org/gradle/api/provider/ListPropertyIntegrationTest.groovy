/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

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
                    assert expected == actual
                    actual.each { assert it instanceof String }
                }
            }
            
            task verify(type: MyTask)
        """
    }

    @Unroll
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

    @Unroll
    def "can set value for string list property using GString values"() {
        buildFile << """
            def str = "aBc"
            verify {
                prop = ${value}
                expected = [ 'a', 'b' ]
            }
        """
        expect:
        succeeds("verify")

        where:
        value                                                                                         | _
        '[ "${str.substring(0, 1)}", "${str.toLowerCase().substring(1, 2)}" ]'                        | _
        'providers.provider { [ "${str.substring(0, 1)}", "${str.toLowerCase().substring(1, 2)}" ] }' | _
    }

    def "can add elements to default value"() {
        buildFile << """
            verify {
                prop = [ 'a' ]
                prop.add('b')
                prop.add(project.provider { 'c' })
                prop.addAll('d', 'e')
                prop.addAll(['f', 'g'])
                prop.addAll(project.provider { [ 'h', 'i' ] })
                expected = [ 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i' ]
            }
        """
        expect:
        succeeds("verify")
    }

    @Unroll
    def "can add element to string list property using GString value"() {
        buildFile << """
            def str = "aBc"
            verify {
                prop = [ 'a' ]
                prop.add($value)
                expected = [ 'a', 'b' ]
            }
        """
        expect:
        succeeds("verify")

        where:
        value                                                           | _
        '"${str.toLowerCase().substring(1, 2)}"'                        | _
        'providers.provider { "${str.toLowerCase().substring(1, 2)}" }' | _
    }

    @Unroll
    def "can add elements to string list property using GString value"() {
        buildFile << """
            def str = "aBc"
            verify {
                prop = [ 'a' ]
                prop.addAll($value)
                expected = [ 'a', 'b', 'c' ]
            }
        """
        expect:
        succeeds("verify")

        where:
        value                                                                                         | _
        '"${str.toLowerCase().substring(1, 2)}", "${str.substring(2, 3)}"'                            | _
        '[ "${str.toLowerCase().substring(1, 2)}", "${str.substring(2, 3)}" ]'                        | _
        'providers.provider { [ "${str.toLowerCase().substring(1, 2)}", "${str.substring(2, 3)}" ] }' | _
    }

    def "can add elements to empty list property"() {
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

    def "adds to non-defined property does nothing"() {
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
