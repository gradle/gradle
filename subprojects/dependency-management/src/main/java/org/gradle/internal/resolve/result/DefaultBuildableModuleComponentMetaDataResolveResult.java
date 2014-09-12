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

import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData;
import org.gradle.internal.resolve.ModuleVersionResolveException;

public class DefaultBuildableModuleComponentMetaDataResolveResult extends DefaultResourceAwareResolveResult implements BuildableModuleComponentMetaDataResolveResult {
    private State state = State.Unknown;
    private ModuleVersionResolveException failure;
    private MutableModuleComponentResolveMetaData metaData;
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

    public void resolved(MutableModuleComponentResolveMetaData metaData) {
        reset(State.Resolved);
        this.metaData = metaData;
        authoritative = true;
    }

    public void missing() {
        reset(State.Missing);
        authoritative = true;
    }

    public void failed(ModuleVersionResolveException failure) {
        reset(State.Failed);
        this.failure = failure;
        authoritative = true;
    }

    public State getState() {
        return state;
    }

    public boolean hasResult() {
        return state != State.Unknown;
    }

    public ModuleVersionResolveException getFailure() {
        assertHasResult();
        return failure;
    }

    public MutableModuleComponentResolveMetaData getMetaData() throws ModuleVersionResolveException {
        assertResolved();
        return metaData;
    }

    public boolean isAuthoritative() {
        assertHasResult();
        return authoritative;
    }

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
