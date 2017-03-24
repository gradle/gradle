/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.transform.VariantSelector
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.internal.component.model.VariantMetadata
import spock.lang.Specification

class DefaultArtifactSetTest extends Specification {
    def componentId = Stub(ComponentIdentifier)
    def exclusions = Stub(ModuleExclusions)
    def schema = Stub(AttributesSchemaInternal)

    def "returns empty set when component id does not match spec"() {
        def variant1 = Stub(VariantMetadata)
        def artifactSet = new DefaultArtifactSet(componentId, null, null, null, [variant1] as Set, schema, null, null, 12L, null, null)

        expect:
        def selected = artifactSet.select({false}, Stub(VariantSelector))
        selected == ResolvedArtifactSet.EMPTY
    }

    def "selects artifacts when component id matches spec"() {
        def variant1 = Stub(VariantMetadata)
        def resolvedVariant1 = Stub(ResolvedVariant)
        def selector = Stub(VariantSelector)
        def artifactSet = new DefaultArtifactSet(componentId, null, null, null, [variant1] as Set, schema, null, null, 12L, null, null)

        given:
        selector.select(_, schema) >> resolvedVariant1

        expect:
        def selected = artifactSet.select({true}, selector)
        selected instanceof SingleVariantResolvedArtifactSet
        selected.variant == resolvedVariant1
    }
}
