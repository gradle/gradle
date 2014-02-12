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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData;

public class DefaultBuildableModuleVersionMetaDataResolveResult implements BuildableModuleVersionMetaDataResolveResult {
    private State state = State.Unknown;
    private ModuleSource moduleSource;
    private ModuleVersionResolveException failure;
    private MutableModuleVersionMetaData metaData;

    private void reset(State state) {
        this.state = state;
        metaData = null;
        failure = null;
        moduleSource = null;
    }

    public void reset() {
        reset(State.Unknown);
    }

    public void resolved(MutableModuleVersionMetaData metaData, ModuleSource moduleSource) {
        reset(State.Resolved);
        this.metaData = metaData;
        this.moduleSource = moduleSource;
    }

    public void missing() {
        reset(State.Missing);
    }

    public void probablyMissing() {
        reset(State.ProbablyMissing);
    }

    public void failed(ModuleVersionResolveException failure) {
        reset(State.Failed);
        this.failure = failure;
    }

    public State getState() {
        return state;
    }

    public ModuleVersionResolveException getFailure() {
        assertHasResult();
        return failure;
    }

    public MutableModuleVersionMetaData getMetaData() throws ModuleVersionResolveException {
        assertResolved();
        return metaData;
    }

    private void assertHasResult() {
        if (state == State.Unknown) {
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

    public ModuleSource getModuleSource() {
        assertResolved();
        return moduleSource;
    }

    public void setModuleSource(ModuleSource moduleSource) {
        assertResolved();
        this.moduleSource = moduleSource;
    }
}
