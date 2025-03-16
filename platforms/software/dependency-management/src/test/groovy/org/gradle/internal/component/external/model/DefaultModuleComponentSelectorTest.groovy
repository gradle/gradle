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

import com.google.common.collect.ImmutableSet
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint
import org.gradle.api.internal.artifacts.capability.DefaultSpecificCapabilitySelector
import org.gradle.api.internal.artifacts.capability.DefaultFeatureCapabilitySelector
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.attributes.ImmutableAttributes
import spock.lang.Specification

import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newProjectId
import static org.gradle.util.AttributeTestUtil.attributes
import static org.gradle.util.Matchers.strictlyEquals

class DefaultModuleComponentSelectorTest extends Specification {
    private static ImmutableVersionConstraint v(String version) {
        return DefaultImmutableVersionConstraint.of(version)
    }

    private static ImmutableVersionConstraint v(String version, String branch) {
        return new DefaultImmutableVersionConstraint("", version, "", [], branch)
    }

    private static ImmutableVersionConstraint b(String branch) {
        return new DefaultImmutableVersionConstraint("", "", "", [], branch)
    }

    def "is instantiated with non-null constructor parameter values"() {
        when:
        def selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('some-group', 'some-name'), v('1.0'), ImmutableAttributes.EMPTY, [] as Set)

        then:
        selector.group == 'some-group'
        selector.module == 'some-name'
        selector.version == '1.0'
        selector.versionConstraint.requiredVersion == '1.0'
        selector.versionConstraint.preferredVersion == ''
        selector.versionConstraint.strictVersion == ''
        selector.versionConstraint.rejectedVersions == []
        selector.displayName == 'some-group:some-name:1.0'
        selector.attributes.empty
        selector.toString() == 'some-group:some-name:1.0'
    }

    def "formats a display name"() {
        expect:
        def versionSelector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('some-group', 'some-name'), v('1.0'))
        versionSelector.displayName == "some-group:some-name:1.0"
        versionSelector.toString() == "some-group:some-name:1.0"

        def branchSelector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('some-group', 'some-name'), b('release'))
        branchSelector.displayName == "some-group:some-name:{branch release}"
        branchSelector.toString() == "some-group:some-name:{branch release}"

        def bothSelector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('some-group', 'some-name'), v('1.0', 'release'))
        bothSelector.displayName == "some-group:some-name:{require 1.0; branch release}"
        bothSelector.toString() == "some-group:some-name:{require 1.0; branch release}"

        def noSelector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('some-group', 'some-name'), v(''))
        noSelector.displayName == "some-group:some-name"
        noSelector.toString() == "some-group:some-name"
    }

    def "can compare with other instance (#group, #name, #version)"() {
        expect:
        def selector1 = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('some-group', 'some-name'), v('1.0'))
        def selector2 = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(group, name), v(version))
        strictlyEquals(selector1, selector2) == equality
        (selector1.hashCode() == selector2.hashCode()) == hashCode
        (selector1.toString() == selector2.toString()) == stringRepresentation

        where:
        group         | name         | version | equality | hashCode | stringRepresentation
        'some-group'  | 'some-name'  | '1.0'   | true     | true     | true
        'other-group' | 'some-name'  | '1.0'   | false    | false    | false
        'some-group'  | 'other-name' | '1.0'   | false    | false    | false
        'some-group'  | 'some-name'  | '2.0'   | false    | false    | false
    }

    def "can create new selector"() {
        when:
        def selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('some-group', 'some-name'), v('1.0'))

        then:
        selector.group == 'some-group'
        selector.module == 'some-name'
        selector.version == '1.0'
        selector.versionConstraint.requiredVersion == '1.0'
        selector.versionConstraint.preferredVersion == ''
        selector.versionConstraint.strictVersion == ''
        selector.versionConstraint.rejectedVersions == []
        selector.displayName == 'some-group:some-name:1.0'
        selector.toString() == 'some-group:some-name:1.0'
    }

    def "can create new selector with attributes"() {
        def customAttr = Attribute.of('custom', String)
        def otherAttr = Attribute.of('other', String)

        when:
        def selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('some-group', 'some-name'), v('1.0'), attributes(custom: 'foo', other: 'bar'), [] as Set)

        then:
        selector.group == 'some-group'
        selector.module == 'some-name'
        selector.version == '1.0'
        selector.versionConstraint.requiredVersion == '1.0'
        selector.versionConstraint.preferredVersion == ''
        selector.versionConstraint.strictVersion == ''
        selector.versionConstraint.rejectedVersions == []
        selector.attributes.keySet() == [customAttr, otherAttr] as Set
        selector.attributes.getAttribute(customAttr) == 'foo'
        selector.attributes.getAttribute(otherAttr) == 'bar'
        selector.displayName == 'some-group:some-name:1.0'
        selector.toString() == 'some-group:some-name:1.0'
    }

    def "can create new selector with capabilities"() {
        def capabilitySelectors = ImmutableSet.of(
            new DefaultSpecificCapabilitySelector(new DefaultImmutableCapability("org", "blah", "1")),
            new DefaultFeatureCapabilitySelector("foo")
        )
        when:
        def selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('some-group', 'some-name'), v('1.0'), ImmutableAttributes.EMPTY, capabilitySelectors)

        then:
        selector.group == 'some-group'
        selector.module == 'some-name'
        selector.version == '1.0'
        selector.versionConstraint.requiredVersion == '1.0'
        selector.versionConstraint.preferredVersion == ''
        selector.versionConstraint.strictVersion == ''
        selector.versionConstraint.rejectedVersions == []
        selector.attributes.isEmpty()
        selector.capabilitySelectors == capabilitySelectors
        selector.requestedCapabilities[0] == ((DefaultSpecificCapabilitySelector) capabilitySelectors[0]).backingCapability
        selector.requestedCapabilities[1] == new DefaultImmutableCapability("some-group", "some-name-foo", "1.0")
        selector.displayName == 'some-group:some-name:1.0'
        selector.toString() == 'some-group:some-name:1.0'
    }

    def "does not match id for unexpected component selector type"() {
        when:
        def selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('some-group', 'some-name'), v('1.0'))
        boolean matches = selector.matchesStrictly(newProjectId(':mypath'))

        then:
        assert !matches
    }

    def "matches id (#group, #name, #version)"() {
        expect:
        def selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('some-group', 'some-name'), v('1.0'))
        def id = new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId(group, name), version)
        selector.matchesStrictly(id) == matchesId

        where:
        group         | name         | version | matchesId
        'some-group'  | 'some-name'  | '1.0'   | true
        'other-group' | 'some-name'  | '1.0'   | false
        'some-group'  | 'other-name' | '1.0'   | false
        'some-group'  | 'some-name'  | '2.0'   | false
    }

}
