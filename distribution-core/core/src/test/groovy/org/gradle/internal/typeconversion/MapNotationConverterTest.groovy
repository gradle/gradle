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
package org.gradle.internal.typeconversion

import org.gradle.api.InvalidUserDataException
import spock.lang.Specification

import javax.annotation.Nullable

class MapNotationConverterTest extends Specification {
    final NotationParser parser = NotationParserBuilder.toType(TargetObject).converter(new DummyConverter()).toComposite()

    def "parses map with required keys"() {
        expect:
        def object = parser.parseNotation([name: 'name', version: 'version'])
        object.key1 == 'name'
        object.key2 == 'version'
        object.prop1 == null
    }

    def "parses map with required and optional keys"() {
        expect:
        def object = parser.parseNotation([name: 'name', version: 'version', optional: '1.2'])
        object.key1 == 'name'
        object.key2 == 'version'
        object.optional == '1.2'
        object.prop1 == null
    }

    def "configures properties of converted object using extra properties"() {
        expect:
        def object = parser.parseNotation([name: 'name', version: 'version', prop1: 'prop1', optional: '1.2'])
        object.key1 == 'name'
        object.key2 == 'version'
        object.prop1 == 'prop1'
    }

    def "does not mutate original map"() {
        def source = [name: 'name', version: 'version', prop1: 'prop1', optional: '1.2']
        def copy = new HashMap<String, Object>(source)

        when:
        parser.parseNotation(source)

        then:
        source == copy
    }

    def "does not parse map with missing keys"() {
        when:
        parser.parseNotation([name: 'name'])

        then:
        InvalidUserDataException e = thrown()
        e.message == 'Required keys [version] are missing from map {name=name}.'
    }

    def "treats empty strings and null values as missing"() {
        when:
        parser.parseNotation([name: null, version: ''])

        then:
        InvalidUserDataException e = thrown()
        e.message.startsWith 'Required keys [name, version] are missing from map '
    }

    def "does not parse map with unknown extra properties"() {
        when:
        parser.parseNotation([name: 'name', version: 1.2, unknown: 'unknown'])

        then:
        MissingPropertyException e = thrown()
        e.property == 'unknown'
        e.type == TargetObject
    }

    def "does not parse notation that is not a map"() {
        when:
        parser.parseNotation('string')

        then:
        thrown(UnsupportedNotationException)
    }

    static class DummyConverter extends MapNotationConverter<TargetObject> {
        protected TargetObject parseMap(@MapKey('name') String name,
                                        @MapKey('version') String version,
                                        @MapKey('optional') @Nullable optional) {
            return new TargetObject(key1:  name, key2:  version, optional:  optional)
        }
    }
}

class TargetObject {
    String key1;
    String key2;
    String optional;
    String prop1;
}
