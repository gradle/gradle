/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.result

import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ComponentSelectionReason
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.util.AttributeTestUtil

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class ResolutionResultDataBuilder {

    static DefaultResolvedDependencyResult newDependency(String group='a', String module='a', String version='1', String selectedVersion='1') {
        new DefaultResolvedDependencyResult(newSelector(group, module, version), false, newModule(group, module, selectedVersion), newVariant("variant"), newModule())
    }

    static DefaultUnresolvedDependencyResult newUnresolvedDependency(String group='x', String module='x', String version='1', String selectedVersion='1') {
        def requested = newSelector(group, module, version)
        org.gradle.internal.Factory<String> broken = { "broken" }
        new DefaultUnresolvedDependencyResult(requested, false, ComponentSelectionReasons.requested(), newModule(group, module, selectedVersion), new ModuleVersionResolveException(newSelector(DefaultModuleIdentifier.newId(group, module), version), broken))
    }

    static DefaultResolvedComponentResult newModule(String group='a', String module='a', String version='1', ComponentSelectionReason selectionReason = ComponentSelectionReasons.requested(), ResolvedVariantResult variant = newVariant(), String repoId = null) {
        new DefaultResolvedComponentResult(newId(group, module, version), selectionReason, new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId(group, module), version), [1L: variant], [variant], repoId)
    }

    static DefaultResolvedDependencyResult newDependency(ComponentSelector componentSelector, String group='a', String module='a', String selectedVersion='1') {
        new DefaultResolvedDependencyResult(componentSelector, false, newModule(group, module, selectedVersion), newVariant("variant"), newModule())
    }

    static ResolvedVariantResult newVariant(String name = 'default', Map<String, String> attributes = [:], String ownerGroup = 'com', String ownerModule = 'foo', String ownerVersion = '1.0') {
        def mutableAttributes = AttributeTestUtil.attributesFactory().mutable()
        attributes.each {
            mutableAttributes.attribute(Attribute.of(it.key, String), it.value)
        }
        def ownerId = DefaultModuleComponentIdentifier.newId(
            newId(ownerGroup, ownerModule, ownerVersion)
        )
        return new DefaultResolvedVariantResult(ownerId, Describables.of(name), mutableAttributes, ImmutableCapabilities.EMPTY, null)
    }

    static ModuleComponentSelector newSelector(String group, String module, String version) {
        DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(group, module), version)
    }
}
