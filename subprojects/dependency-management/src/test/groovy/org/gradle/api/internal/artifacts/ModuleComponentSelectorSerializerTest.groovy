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

package org.gradle.api.internal.artifacts

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.DesugaredAttributeContainerSerializer
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.util.TestUtil

import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector
import static org.gradle.util.AttributeTestUtil.attributes
import static org.gradle.util.AttributeTestUtil.attributesFactory

class ModuleComponentSelectorSerializerTest extends SerializerSpec {
    private final static ModuleIdentifier UTIL = DefaultModuleIdentifier.newId("org", "util")

    private serializer = new ModuleComponentSelectorSerializer(new DesugaredAttributeContainerSerializer(attributesFactory(), TestUtil.objectInstantiator()))

    def "serializes"() {
        when:
        def result = serialize(newSelector(UTIL, constraint(version, strict, rejects), attributes(foo: 'bar'), [capability("foo")]), serializer)

        then:
        result == newSelector(UTIL, constraint(version, strict, rejects), attributes(foo: 'bar'), [capability("foo")])

        where:
        version | strict   | rejects
        '5.0'   | ''       | []
        '5.0'   | ''       | ['1.0']
        '5.0'   | ''       | ['1.0', '2.0']
        '5.0'   | '[1.0,)' | []
    }

    def "serializes branch"() {
        when:
        def constraint = new DefaultMutableVersionConstraint('')
        constraint.branch = 'custom-branch'
        def result = serialize(newSelector(UTIL, constraint, ImmutableAttributes.EMPTY, []), serializer)

        then:
        result == newSelector(UTIL, constraint, ImmutableAttributes.EMPTY, [])
    }

    private static MutableVersionConstraint constraint(String version, String strictVersion, List<String> rejectedVersions) {
        MutableVersionConstraint constraint = new DefaultMutableVersionConstraint(version)
        if (strictVersion != null) {
            constraint.strictly(strictVersion)
        }
        for (String reject : rejectedVersions) {
            constraint.reject(reject)
        }
        return constraint
    }

    private static Capability capability(String name) {
        return new DefaultImmutableCapability("test", name, "1.16")
    }
}
