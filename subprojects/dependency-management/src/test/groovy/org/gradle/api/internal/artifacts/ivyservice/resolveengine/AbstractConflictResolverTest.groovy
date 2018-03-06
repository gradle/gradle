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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.dependencies.DefaultResolvedVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentStateWithDependents
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultConflictResolverDetails
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons
import org.gradle.internal.component.model.ComponentResolveMetadata
import spock.lang.Specification

abstract class AbstractConflictResolverTest extends Specification {

    List<ComponentResolutionState> participants = []
    ConflictResolverDetails<ComponentResolutionState> details = new DefaultConflictResolverDetails([], participants)

    ModuleConflictResolver resolver
    TestComponent root = new TestComponent(DefaultModuleVersionIdentifier.newId('', 'root', ''))

    protected void resolveConflicts() {
        resolver.select(details)
    }

    protected TestComponent module(String org, String name, String version, TestComponent... dependents) {
        ComponentResolutionState state = new TestComponent(DefaultModuleVersionIdentifier.newId(org, name, version))
        if (dependents) {
            Collections.addAll(state.dependents, dependents)
        } else {
            state.dependents << root
        }
        state
    }

    protected TestComponent prefer(String version, TestComponent... dependents) {
        module('org', 'foo', version, dependents).with {
            participants << it
            it
        }
    }

    protected TestComponent strictly(String version, TestComponent... dependents) {
        prefer(version, dependents).strict()
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

    private static class TestComponent implements ComponentStateWithDependents<TestComponent> {

        private final ModuleVersionIdentifier id
        private MutableVersionConstraint constraint
        private ComponentResolveMetadata metaData
        private List<TestComponent> dependents = []

        TestComponent(ModuleVersionIdentifier id) {
            this.id = id
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

        TestComponent release() {
            metaData = ['getStatus': {'release'}] as ComponentResolveMetadata
            this
        }

        @Override
        ModuleVersionIdentifier getId() {
            id
        }

        @Override
        ComponentResolveMetadata getMetaData() {
            metaData
        }

        @Override
        ResolvedVersionConstraint getVersionConstraint() {
            new DefaultResolvedVersionConstraint(constraint, new DefaultVersionSelectorScheme(new DefaultVersionComparator()))
        }

        @Override
        boolean isResolved() {
            metaData != null
        }

        @Override
        ComponentSelectionReasonInternal getSelectionReason() {
            VersionSelectionReasons.requested()
        }

        @Override
        void addCause(ComponentSelectionDescriptorInternal componentSelectionDescription) {

        }

        @Override
        String getVersion() {
            id.version
        }

        String toString() { id }

        @Override
        List<TestComponent> getDependents() {
            dependents
        }

        @Override
        List<TestComponent> getUnattachedDependencies() {
            []
        }

        @Override
        boolean isFromPendingNode() {
            false
        }
    }
}
