/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors

import org.gradle.api.artifacts.ClientModule
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.resolve.result.ComponentIdResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableComponentIdResolveResult

class TestProjectSelectorState implements ResolvableSelectorState {
    public static final String VERSION = "1"
    private final ProjectComponentIdentifier projectId

    TestProjectSelectorState(ProjectComponentIdentifier projectId) {
        this.projectId = projectId
    }

    @Override
    ResolvedVersionConstraint getVersionConstraint() {
        return null
    }


    @Override
    ComponentSelector getSelector() {
        return DefaultProjectComponentSelector.newSelector(projectId)
    }

    @Override
    ComponentIdResolveResult resolve(VersionSelector allRejects) {
        def result = new DefaultBuildableComponentIdResolveResult()
        result.resolved(projectId, DefaultModuleVersionIdentifier.newId("org", projectId.projectName, VERSION))
        return result
    }

    @Override
    ComponentIdResolveResult resolvePrefer(VersionSelector allRejects) {
        return null
    }

    @Override
    void markResolved() {
    }

    @Override
    boolean isForce() {
        return false
    }

    @Override
    boolean isSoftForce() {
        return false
    }

    @Override
    boolean isFromLock() {
        return false
    }

    @Override
    boolean hasStrongOpinion() {
        return false
    }

    @Override
    IvyArtifactName getFirstDependencyArtifact() {
        return null
    }

    @Override
    ClientModule getClientModule() {
        return null
    }

    @Override
    boolean isChanging() {
        return false
    }
}

