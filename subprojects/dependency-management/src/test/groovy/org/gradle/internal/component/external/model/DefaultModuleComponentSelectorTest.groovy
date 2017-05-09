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
import org.gradle.api.artifacts.component.ModuleComponentSelector
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newProjectId
import static org.gradle.util.Matchers.strictlyEquals

class DefaultModuleComponentSelectorTest extends Specification {
    def "is instantiated with non-null constructor parameter values"() {
        when:
        ModuleComponentSelector defaultModuleComponentSelector = new DefaultModuleComponentSelector('some-group', 'some-name', '1.0')

        then:
        defaultModuleComponentSelector.group == 'some-group'
        defaultModuleComponentSelector.module == 'some-name'
        defaultModuleComponentSelector.version == '1.0'
        defaultModuleComponentSelector.displayName == 'some-group:some-name:1.0'
        defaultModuleComponentSelector.toString() == 'some-group:some-name:1.0'
    }

    @Unroll
    def "is instantiated with null constructor parameter values (#group, #name, #version)"() {
        when:
        new DefaultModuleComponentSelector(group, name, version)

        then:
        Throwable t = thrown(AssertionError)
        assert t.message == assertionMessage

        where:
        group        | name        | version | assertionMessage
        null         | 'some-name' | '1.0'   | 'group cannot be null'
        'some-group' | null        | '1.0'   | 'module cannot be null'
        'some-group' | 'some-name' | null    | 'version cannot be null'
    }

    @Unroll
    def "can compare with other instance (#group, #name, #version)"() {
        expect:
        ModuleComponentSelector defaultModuleComponentSelector1 = new DefaultModuleComponentSelector('some-group', 'some-name', '1.0')
        ModuleComponentSelector defaultModuleComponentSelector2 = new DefaultModuleComponentSelector(group, name, version)
        strictlyEquals(defaultModuleComponentSelector1, defaultModuleComponentSelector2) == equality
        (defaultModuleComponentSelector1.hashCode() == defaultModuleComponentSelector2.hashCode()) == hashCode
        (defaultModuleComponentSelector1.toString() == defaultModuleComponentSelector2.toString()) == stringRepresentation

        where:
        group         | name         | version | equality | hashCode | stringRepresentation
        'some-group'  | 'some-name'  | '1.0'   | true     | true     | true
        'other-group' | 'some-name'  | '1.0'   | false    | false    | false
        'some-group'  | 'other-name' | '1.0'   | false    | false    | false
        'some-group'  | 'some-name'  | '2.0'   | false    | false    | false
    }

    def "can create new selector"() {
        when:
        ModuleComponentSelector defaultModuleComponentSelector = DefaultModuleComponentSelector.newSelector('some-group', 'some-name', '1.0')

        then:
        defaultModuleComponentSelector.group == 'some-group'
        defaultModuleComponentSelector.module == 'some-name'
        defaultModuleComponentSelector.version == '1.0'
        defaultModuleComponentSelector.displayName == 'some-group:some-name:1.0'
        defaultModuleComponentSelector.toString() == 'some-group:some-name:1.0'
    }

    def "prevents matching of null id"() {
        when:
        ModuleComponentSelector defaultModuleComponentSelector = new DefaultModuleComponentSelector('some-group', 'some-name', '1.0')
        defaultModuleComponentSelector.matchesStrictly(null)

        then:
        Throwable t = thrown(AssertionError)
        assert t.message == 'identifier cannot be null'
    }

    def "does not match id for unexpected component selector type"() {
        when:
        ModuleComponentSelector defaultModuleComponentSelector = new DefaultModuleComponentSelector('some-group', 'some-name', '1.0')
        boolean matches = defaultModuleComponentSelector.matchesStrictly(newProjectId(':mypath'))

        then:
        assert !matches
    }

    @Unroll
    def "matches id (#group, #name, #version)"() {
        expect:
        ModuleComponentSelector defaultModuleComponentSelector = new DefaultModuleComponentSelector('some-group', 'some-name', '1.0')
        ModuleComponentIdentifier defaultModuleComponentIdentifier = new DefaultModuleComponentIdentifier(group, name, version)
        defaultModuleComponentSelector.matchesStrictly(defaultModuleComponentIdentifier) == matchesId

        where:
        group         | name         | version | matchesId
        'some-group'  | 'some-name'  | '1.0'   | true
        'other-group' | 'some-name'  | '1.0'   | false
        'some-group'  | 'other-name' | '1.0'   | false
        'some-group'  | 'some-name'  | '2.0'   | false
    }
}
