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

package org.gradle.internal.resolve.result;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState;
import org.gradle.internal.resolve.ModuleVersionNotFoundException;
import org.gradle.internal.resolve.ModuleVersionResolveException;

public class DefaultBuildableComponentResolveResult extends DefaultResourceAwareResolveResult implements BuildableComponentResolveResult {
    private ComponentGraphResolveState state;
    private ComponentGraphSpecificResolveState graphState;
    private ModuleVersionResolveException failure;

    public DefaultBuildableComponentResolveResult() {
    }

    @Override
    public DefaultBuildableComponentResolveResult failed(ModuleVersionResolveException failure) {
        state = null;
        this.failure = failure;
        return this;
    }

    @Override
    public void notFound(ModuleComponentIdentifier versionIdentifier) {
        failed(new ModuleVersionNotFoundException(DefaultModuleVersionIdentifier.newId(versionIdentifier), getAttempted()));
    }

    @Override
    public void resolved(ComponentGraphResolveState state, ComponentGraphSpecificResolveState graphState) {
        this.state = state;
        this.graphState = graphState;
    }

    @Override
    public void setResult(ComponentGraphResolveState state) {
        assertResolved();
        this.state = state;
    }

    @Override
    public ComponentIdentifier getId() {
        assertResolved();
        return state.getId();
    }

    @Override
    public ModuleVersionIdentifier getModuleVersionId() throws ModuleVersionResolveException {
        assertResolved();
        return state.getMetadata().getModuleVersionId();
    }

    @Override
    public ComponentGraphResolveState getState() throws ModuleVersionResolveException {
        assertResolved();
        return state;
    }

    @Override
    public ComponentGraphSpecificResolveState getGraphState() throws ModuleVersionResolveException {
        assertResolved();
        return graphState;
    }

    @Override
    public ModuleVersionResolveException getFailure() {
        assertHasResult();
        return failure;
    }

    private void assertResolved() {
        assertHasResult();
        if (failure != null) {
            throw failure;
        }
    }

    private void assertHasResult() {
        if (!hasResult()) {
            throw new IllegalStateException("No result has been specified.");
        }
    }

    @Override
    public boolean hasResult() {
        return failure != null || state != null;
    }

    public void applyTo(BuildableComponentIdResolveResult idResolve) {
        super.applyTo(idResolve);
        if (failure != null) {
            idResolve.failed(failure);
        }
        if (state != null) {
            idResolve.resolved(state, graphState);
        }
    }

    public void applyTo(BuildableComponentResolveResult target) {
        super.applyTo(target);
        if (failure != null) {
            target.failed(failure);
        }
        if (state != null) {
            target.resolved(state, graphState);
        }
    }
}
