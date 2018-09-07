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

package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.InvalidUserDataException
import org.gradle.api.capabilities.Capability
import org.gradle.internal.typeconversion.NotationParser
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class CapabilityNotationParserFactoryTest extends Specification {
    @Subject
    private NotationParser<Object, Capability> parser = new CapabilityNotationParserFactory().create()

    def "can parse string notation"() {
        when:
        def capability = parser.parseNotation("foo:bar:1.0")

        then:
        capability.group == 'foo'
        capability.name == 'bar'
        capability.version == '1.0'
    }

    @Unroll
    def "invalid string notation #notation is reported"() {
        when:
        parser.parseNotation(notation)

        then:
        def ex = thrown InvalidUserDataException
        ex.message == "Invalid format for capability: '$notation'. The correct notation is a 3-part group:name:version notation, e.g: 'org.group:capability:1.0'"

        where:
        notation << [
            "foo:bar",
            "foo:bar:",
            "foo::1.0",
            ":bar:1.0"
        ]
    }

    def "can parse map notation"() {
        when:
        def capability = parser.parseNotation(group: 'foo', name: "bar", version: "1.0")

        then:
        capability.group == 'foo'
        capability.name == 'bar'
        capability.version == '1.0'
    }

    @Unroll
    def "invalid map notation #notation is reported"() {
        when:
        parser.parseNotation(notation)

        then:
        def ex = thrown InvalidUserDataException
        ex.message == error

        where:
        notation                     | error
        [group: 'foo']               | "Required keys [name, version] are missing from map {group=foo}."
        [name: 'foo']                | "Required keys [group, version] are missing from map {name=foo}."
        [name: 'foo', version: 'v1'] | "Required keys [group] are missing from map {name=foo, version=v1}."
        [name: null]                 | "Required keys [group, name, version] are missing from map {name=null}."
    }

}
