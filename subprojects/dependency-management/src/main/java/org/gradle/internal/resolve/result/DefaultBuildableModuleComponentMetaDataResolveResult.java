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

import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;

public class DefaultBuildableModuleComponentMetaDataResolveResult extends DefaultResourceAwareResolveResult implements BuildableModuleComponentMetaDataResolveResult {
    private State state = State.Unknown;
    private ModuleVersionResolveException failure;
    private ModuleComponentResolveMetadata metaData;
    private boolean authoritative;

    private void reset(State state) {
        this.state = state;
        metaData = null;
        failure = null;
        authoritative = false;
    }

    public void reset() {
        reset(State.Unknown);
    }

    @Override
    public void resolved(ModuleComponentResolveMetadata metaData) {
        reset(State.Resolved);
        this.metaData = metaData;
        authoritative = true;
    }

    @Override
    public void setMetadata(ModuleComponentResolveMetadata metaData) {
        assertResolved();
        this.metaData = metaData;
    }

    @Override
    public void missing() {
        reset(State.Missing);
        authoritative = true;
    }

    @Override
    public void failed(ModuleVersionResolveException failure) {
        reset(State.Failed);
        this.failure = failure;
        authoritative = true;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public boolean hasResult() {
        return state != State.Unknown;
    }

    @Override
    public ModuleVersionResolveException getFailure() {
        assertHasResult();
        return failure;
    }

    @Override
    public ModuleComponentResolveMetadata getMetaData() throws ModuleVersionResolveException {
        assertResolved();
        return metaData;
    }

    @Override
    public boolean isAuthoritative() {
        assertHasResult();
        return authoritative;
    }

    @Override
    public void setAuthoritative(boolean authoritative) {
        assertHasResult();
        this.authoritative = authoritative;
    }

    private void assertHasResult() {
        if (!hasResult()) {
            throw new IllegalStateException("No result has been specified.");
        }
    }

    private void assertResolved() {
        if (state == State.Failed) {
            throw failure;
        }
        if (state != State.Resolved) {
            throw new IllegalStateException("This module has not been resolved.");
        }
    }
}
