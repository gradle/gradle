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

/**
 * by Szczepan Faber, created at: 10/14/11
 */
public class ForcedModuleNotationParserSpec extends Specification {

    def "understands group:name:version notation"() {
        when:
        def v = new ForcedModuleNotationParser().parseNotation("org.foo:bar:1.0") as List

        then:
        v.size() == 1
        v[0].group == 'org.foo'
        v[0].name  == 'bar'
        v[0].version  == '1.0'
    }

    def "works with CharSequences"() {
        when:
        def sb = new StringBuilder().append("org.foo:charsequence:1.0")
        def v = new ForcedModuleNotationParser().parseNotation(sb) as List

        then:
        v.size() == 1
        v[0].name  == 'charsequence'
    }

    def "allows exact type on input"() {
        def id = ForcedModuleNotationParser.selector("org.foo", "bar", "2.0")

        when:
        def v = new ForcedModuleNotationParser().parseNotation(id) as List

        then:
        v.size() == 1
        v[0].group == 'org.foo'
        v[0].name  == 'bar'
        v[0].version  == '2.0'
    }

    def "allows list of objects on input"() {
        def id = ForcedModuleNotationParser.selector("org.foo", "bar", "2.0")

        when:
        def v = new ForcedModuleNotationParser().parseNotation([id, ["hey:man:1.0"], [group:'i', name:'like', version:'maps']]) as List

        then:
        v.size() == 3
        v[0].name == 'bar'
        v[1].name == 'man'
        v[2].name == 'like'
    }

    def "allows map on input"() {
        when:
        def v = new ForcedModuleNotationParser().parseNotation([group: 'org.foo', name: 'bar', version:'1.0']) as List

        then:
        v.size() == 1
        v[0].group == 'org.foo'
        v[0].name  == 'bar'
        v[0].version  == '1.0'
    }

    def "fails for unknown types"() {
        when:
        new ForcedModuleNotationParser().parseNotation(new Object())

        then:
        thrown(InvalidUserDataException)
    }

    def "reports missing keys for map notation"() {
        when:
        new ForcedModuleNotationParser().parseNotation([name: "bar", version: "1.0"])

        then:
        thrown(InvalidUserDataException)
    }

    def "reports wrong keys for map notation"() {
        when:
        new ForcedModuleNotationParser().parseNotation([groop: 'groop', name: "bar", version: "1.0"])

        then:
        thrown(InvalidUserDataException)
    }

    def "reports invalid format for string notation"() {
        when:
        new ForcedModuleNotationParser().parseNotation(["blahblah"])

        then:
        thrown(InvalidUserDataException)
    }

    def "reports invalid missing data for string notation"() {
        when:
        new ForcedModuleNotationParser().parseNotation([":foo:"])

        then:
        def ex = thrown(InvalidUserDataException)
        ex.message.contains 'cannot be empty'
    }
}
