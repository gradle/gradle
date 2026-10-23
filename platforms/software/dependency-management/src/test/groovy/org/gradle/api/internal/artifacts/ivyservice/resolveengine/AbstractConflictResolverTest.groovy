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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultConflictResolverDetails
import org.gradle.internal.component.model.ComponentGraphResolveMetadata
import spock.lang.Specification

abstract class AbstractConflictResolverTest extends Specification {

    List<ComponentResolutionState> participants = []
    ConflictResolverDetails<ComponentResolutionState> details = new DefaultConflictResolverDetails(participants)

    ModuleConflictResolver resolver

    protected void resolveConflicts() {
        resolver.select(details)
    }

    protected ComponentResolutionState prefer(String version) {
        participant(version, null)
    }

    protected ComponentResolutionState strictly(String version) {
        prefer(version)
    }

    protected ComponentResolutionState release(String version) {
        participant(version, Stub(ComponentGraphResolveMetadata) {
            getStatus() >> 'release'
        })
    }

    private ComponentResolutionState participant(String version, ComponentGraphResolveMetadata metadata) {
        def component = Stub(ComponentResolutionState) {
            getVersion() >> version
            getMetadataOrNull() >> metadata
        }
        participants << component
        component
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
}
