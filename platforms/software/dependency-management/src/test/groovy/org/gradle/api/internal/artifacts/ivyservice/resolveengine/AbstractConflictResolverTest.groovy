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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.VirtualPlatformState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultConflictResolverDetails
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.model.ComponentGraphResolveMetadata
import spock.lang.Specification

abstract class AbstractConflictResolverTest extends Specification {

    List<ComponentResolutionState> participants = []
    ConflictResolverDetails<ComponentResolutionState> details = new DefaultConflictResolverDetails(participants)

    ModuleConflictResolver resolver
    TestComponent root = new TestComponent(DefaultModuleVersionIdentifier.newId('', 'root', ''))

    protected void resolveConflicts() {
        resolver.select(details)
    }

    protected TestComponent module(String org, String name, String version) {
        new TestComponent(DefaultModuleVersionIdentifier.newId(org, name, version))
    }

    protected TestComponent prefer(String version) {
        module('org', 'foo', version).with {
            participants << it
            it
        }
    }

    protected TestComponent strictly(String version) {
        prefer(version).strict()
    }

    protected void selected(ComponentResolutionState state) {
        assert details.hasSelected()
        assert details.selected == state
    }

    protected void selected(String version) {
        assert details.hasSelected()
        assert details.selected.version == version
    }

    protected void resolutionFailedWith(String message) {
        assert details.hasFailure()
        assert details.failure.message == message
    }

    protected void candidateVersions(List<String> versions) {
        versions.each {
            prefer(it)
        }
    }

    private static class TestComponent implements ComponentResolutionState {
        final ModuleVersionIdentifier id
        final ComponentIdentifier componentId
        ComponentGraphResolveMetadata metadata
        boolean rejected = false
        private MutableVersionConstraint constraint
        String repositoryName

        TestComponent(ModuleVersionIdentifier id) {
            this.id = id
            this.componentId = DefaultModuleComponentIdentifier.newId(id)
            this.constraint = new DefaultMutableVersionConstraint(id.version)
        }

        TestComponent strict() {
            constraint.strictly(id.version)
            this
        }

        TestComponent rejectAll() {
            constraint.rejectAll()
            this
        }

        @Override
        ComponentGraphResolveMetadata getMetadataOrNull() {
            return metadata
        }

        TestComponent release() {
            metadata = ['getStatus': {'release'}] as ComponentGraphResolveMetadata
            this
        }

        @Override
        void addCause(ComponentSelectionDescriptorInternal componentSelectionDescriptor) {

        }

        @Override
        void reject() {

        }

        @Override
        String getVersion() {
            id.version
        }

        String toString() { id }

        @Override
        Set<VirtualPlatformState> getPlatformOwners() {
            Collections.emptySet()
        }

        @Override
        VirtualPlatformState getPlatformState() {
        }
    }
}
