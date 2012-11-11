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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;

public class DefaultBuildableModuleVersionDescriptor implements BuildableModuleVersionDescriptor {
    private ModuleDescriptor moduleDescriptor;
    private boolean changing;
    private State state = State.Unknown;
    private ModuleVersionResolveException failure;

    public void reset(State state) {
        this.state = state;
        moduleDescriptor = null;
        changing = false;
        failure = null;
    }

    public void resolved(ModuleDescriptor descriptor, boolean changing) {
        reset(State.Resolved);
        moduleDescriptor = descriptor;
        this.changing = changing;
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

    public ModuleRevisionId getId() {
        assertResolved();
        return moduleDescriptor.getResolvedModuleRevisionId();
    }

    public ModuleDescriptor getDescriptor() {
        assertResolved();
        return moduleDescriptor;
    }

    public boolean isChanging() {
        assertResolved();
        return changing;
    }
}
