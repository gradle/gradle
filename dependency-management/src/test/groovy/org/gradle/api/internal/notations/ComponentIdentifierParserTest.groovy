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

package org.gradle.api.internal.notations

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.typeconversion.NotationParser
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ComponentIdentifierParserTest extends Specification {

    @Subject
    NotationParser<Object, ComponentIdentifier> parser = new ComponentIdentifierParserFactory().create()

    @Unroll("Parses #notation")
    def "can parse a module component identifier"() {
        when:
        def id = parser.parseNotation(notation)

        then:
        id instanceof ModuleComponentIdentifier
        id.moduleIdentifier.group == expectedGroup
        id.moduleIdentifier.name == expectedName
        id.version == expectedVersion

        where:
        notation                                                  | expectedGroup | expectedName | expectedVersion
        'g:a:v'                                                   | 'g'           | 'a'          | 'v'
        'group:name:1.0'                                          | 'group'       | 'name'       | '1.0'
        'group  :name  :1.0  '                                    | 'group'       | 'name'       | '1.0'
        "${-> 'group'}:name:1.1"                                  | 'group'       | 'name'       | '1.1'
        [group: 'group', name: 'foo', version: '1.4-beta-1']      | 'group'       | 'foo'        | '1.4-beta-1'
        [group: ' group ', name: 'foo ', version: '  1.4-beta-1'] | 'group'       | 'foo'        | '1.4-beta-1'
    }

    @Unroll("Fails to parse #notation")
    def "fails if string doesn't have 3 parts"() {
        when:
        parser.parseNotation(notation)

        then:
        InvalidUserDataException ex = thrown()
        ex.message == "Invalid module component notation: $notation : must be a valid 3 part identifier, eg.: org.gradle:gradle:1.0"

        where:
        notation << ["foo", "foo:", "foo:bar", "foo:bar:", "foo:bar:baz:qux"]
    }

    @Unroll("Fails to parse #notation")
    def "fails to parse map notation which doesn't pass validation"() {
        when:
        parser.parseNotation(notation)

        then:
        Exception ex = thrown()
        ex.message.startsWith(error)

        where:
        notation                                    | error
        [:]                                         | "Required keys [group, name, version] are missing from map {}."
        [group: 'foo']                              | "Required keys [name, version] are missing from map {group=foo}."
        [name: 'foo']                               | "Required keys [group, version] are missing from map {name=foo}."
        [version: 'foo']                            | "Required keys [group, name] are missing from map {version=foo}."
        [group: 'foo', name: 'module']              | "Required keys [version] are missing from map {group=foo, name=module}."
        [group: 'foo', version: '1.0']              | "Required keys [name] are missing from map {group=foo, version=1.0}."
        [name: 'foo', version: '1.0']               | "Required keys [group] are missing from map {name=foo, version=1.0}."
        [group: '()', name: 'foo', version: '1.0']  | "Cannot convert the provided notation to an object of type ComponentIdentifier: ()."
        [group: 'name', name: '*', version: '1.0']  | "Cannot convert the provided notation to an object of type ComponentIdentifier: *."
        [group: 'name', name: 'name', version: '['] | "Cannot convert the provided notation to an object of type ComponentIdentifier: [."

    }
}
