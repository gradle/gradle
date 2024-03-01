/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.component.external.model

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import spock.lang.Specification

import static org.gradle.util.Matchers.strictlyEquals

class DefaultModuleComponentIdentifierTest extends Specification {
    def "is instantiated with non-null constructor parameter values"() {
        when:
        ModuleComponentIdentifier defaultModuleComponentIdentifier = new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('some-group', 'some-name'), '1.0')

        then:
        defaultModuleComponentIdentifier.group == 'some-group'
        defaultModuleComponentIdentifier.module == 'some-name'
        defaultModuleComponentIdentifier.version == '1.0'
        defaultModuleComponentIdentifier.displayName == 'some-group:some-name:1.0'
        defaultModuleComponentIdentifier.toString() == 'some-group:some-name:1.0'
    }

    def "is instantiated with null constructor parameter values (#group, #name, #version)"() {
        when:
        new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId(group, name), version)

        then:
        thrown(AssertionError)

        where:
        group        | name        | version
        null         | 'some-name' | '1.0'
        'some-group' | null        | '1.0'
        'some-group' | 'some-name' | null
    }

    def "can compare with other instance (#group, #name, #version)"() {
        expect:
        ModuleComponentIdentifier defaultModuleComponentIdentifier1 = new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('some-group', 'some-name'), '1.0')
        ModuleComponentIdentifier defaultModuleComponentIdentifier2 = new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId(group, name), version)
        strictlyEquals(defaultModuleComponentIdentifier1, defaultModuleComponentIdentifier2) == equality
        (defaultModuleComponentIdentifier1.hashCode() == defaultModuleComponentIdentifier2.hashCode()) == hashCode
        (defaultModuleComponentIdentifier1.toString() == defaultModuleComponentIdentifier2.toString()) == stringRepresentation

        where:
        group         | name         | version | equality | hashCode | stringRepresentation
        'some-group'  | 'some-name'  | '1.0'   | true     | true     | true
        'other-group' | 'some-name'  | '1.0'   | false    | false    | false
        'some-group'  | 'other-name' | '1.0'   | false    | false    | false
        'some-group'  | 'some-name'  | '2.0'   | false    | false    | false
    }

    def "can create new ID"() {
        when:
        ModuleComponentIdentifier defaultModuleComponentIdentifier = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId('some-group', 'some-name'), '1.0')

        then:
        defaultModuleComponentIdentifier.group == 'some-group'
        defaultModuleComponentIdentifier.module == 'some-name'
        defaultModuleComponentIdentifier.version == '1.0'
        defaultModuleComponentIdentifier.displayName == 'some-group:some-name:1.0'
        defaultModuleComponentIdentifier.toString() == 'some-group:some-name:1.0'
    }
}
