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

package org.gradle.groovy.scripts.internal

import spock.lang.Specification
import spock.lang.Unroll

class CompatibilityExtensionTest extends Specification {
    @Unroll
    def "should not throw error if calling #type .addAll(null)"() {
        given:
        def collection = Mock(type)

        expect:
        collection.addAll(null) == false

        where:
        type << [List, LinkedList, ArrayList]
    }

    @Unroll
    def "should not throw error if calling List.addAll(#value)"() {
        given:
        def list = []

        expect:
        // directly calling the extension here, because it should in theory
        // not happen from Groovy code, but we wrote the code in order to make
        // sure this doesn't happen from user build code
        CompatibilityExtension.addAll(list, value) == added

        and:
        list == result

        where:
        value                                             | added | result
        []                                                | false | []
        ['a']                                             | true  | ['a']
        ['a'] as String[]                                 | true  | ['a']
        Mock(Iterator)                                    | false | []
        [iterator: { [].iterator() }] as Iterable         | false | []
        ['a', 'b'].iterator()                             | true  | ['a', 'b']
        [iterator: { ['a', 'b'].iterator() }] as Iterable | true  | ['a', 'b']
        'object'                                          | true  | ['object']
    }
}
