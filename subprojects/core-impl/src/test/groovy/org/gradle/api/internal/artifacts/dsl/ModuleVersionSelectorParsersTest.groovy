/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl;


import org.gradle.api.InvalidUserDataException
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import static org.gradle.api.internal.artifacts.dsl.ModuleVersionSelectorParsers.multiParser
import static org.gradle.api.internal.artifacts.dsl.ModuleVersionSelectorParsers.parser

public class ModuleVersionSelectorParsersTest extends Specification {

    def "understands group:name:version notation"() {
        when:
        def v = multiParser().parseNotation("org.foo:bar:1.0") as List

        then:
        v.size() == 1
        v[0].group == 'org.foo'
        v[0].name  == 'bar'
        v[0].version  == '1.0'
    }

    def "works with CharSequences"() {
        when:
        def sb = new StringBuilder().append("org.foo:charsequence:1.0")
        def v = multiParser().parseNotation(sb) as List

        then:
        v.size() == 1
        v[0].name  == 'charsequence'
    }

    def "allows exact type on input"() {
        def id = newSelector("org.foo", "bar", "2.0")

        when:
        def v = multiParser().parseNotation(id) as List

        then:
        v.size() == 1
        v[0].group == 'org.foo'
        v[0].name  == 'bar'
        v[0].version  == '2.0'
    }

    def "allows list of objects on input"() {
        def id = newSelector("org.foo", "bar", "2.0")

        when:
        def v = multiParser().parseNotation([id, ["hey:man:1.0"], [group:'i', name:'like', version:'maps']]) as List

        then:
        v.size() == 3
        v[0].name == 'bar'
        v[1].name == 'man'
        v[2].name == 'like'
    }

    def "allows map on input"() {
        when:
        def v = multiParser().parseNotation([group: 'org.foo', name: 'bar', version:'1.0']) as List

        then:
        v.size() == 1
        v[0].group == 'org.foo'
        v[0].name  == 'bar'
        v[0].version  == '1.0'
    }

    def "fails for unknown types"() {
        when:
        multiParser().parseNotation(new Object())

        then:
        thrown(InvalidUserDataException)
    }

    def "reports missing keys for map notation"() {
        when:
        multiParser().parseNotation([name: "bar", version: "1.0"])

        then:
        thrown(InvalidUserDataException)
    }

    def "reports wrong keys for map notation"() {
        when:
        multiParser().parseNotation([groop: 'groop', name: "bar", version: "1.0"])

        then:
        thrown(InvalidUserDataException)
    }

    def "reports invalid format for string notation"() {
        when:
        multiParser().parseNotation(["blahblah"])

        then:
        thrown(InvalidUserDataException)
    }

    def "reports invalid missing data for string notation"() {
        when:
        multiParser().parseNotation([":foo:"])

        then:
        def ex = thrown(InvalidUserDataException)
        ex.message.contains 'cannot be empty'
    }

    def "null is an invalid input"() {
        when:
        multiParser().parseNotation(null)

        then:
        thrown(InvalidUserDataException)

        when:
        parser().parseNotation(null)

        then:
        thrown(InvalidUserDataException)
    }

    def "single parser understands String notation"() {
        //just smoke testing the single parser, it is covered in multiParser, too.
        when:
        def v = parser().parseNotation("org.foo:bar:1.0")

        then:
        v.group == 'org.foo'
        v.name  == 'bar'
        v.version  == '1.0'
    }
}
