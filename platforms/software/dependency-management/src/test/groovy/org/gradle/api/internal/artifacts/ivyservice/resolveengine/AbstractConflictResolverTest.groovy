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

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
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

    protected void resolveConflicts() {
        resolver.select(details)
    }

    protected TestComponent module(String org, String name, String version) {
        new TestComponent(DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(org, name), version))
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
        private final ModuleComponentIdentifier id
        ComponentGraphResolveMetadata metadata
        boolean rejected = false
        private MutableVersionConstraint constraint
        String repositoryName

        TestComponent(ModuleComponentIdentifier id) {
            this.id = id
            this.constraint = new DefaultMutableVersionConstraint(id.version)
        }

        @Override
        ModuleComponentIdentifier getId() {
            return id
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

        String toString() {
            id.toString()
        }

        @Override
        Set<VirtualPlatformState> getPlatformOwners() {
            Collections.emptySet()
        }

        @Override
        VirtualPlatformState getPlatformState() {
        }

        @Override
        ModuleResolutionState getModule() {
            return new ModuleResolutionState() {
                @Override
                ModuleIdentifier getId() {
                    return TestComponent.this.id.moduleIdentifier
                }
            }
        }
    }
}
