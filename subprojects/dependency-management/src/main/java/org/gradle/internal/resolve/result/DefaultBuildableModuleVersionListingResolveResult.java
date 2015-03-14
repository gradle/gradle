/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.internal.resolve.ModuleVersionResolveException;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultBuildableModuleVersionListingResolveResult extends DefaultResourceAwareResolveResult implements BuildableModuleVersionListingResolveResult {
    private State state = State.Unknown;
    private ModuleVersionResolveException failure;
    private Set<String> versions;
    private boolean authoritative;

    private void reset(State state) {
        this.state = state;
        versions = null;
        failure = null;
        authoritative = false;
    }

    public State getState() {
        return state;
    }

    public boolean hasResult() {
        return state != State.Unknown;
    }

    public Set<String> getVersions() throws ModuleVersionResolveException {
        assertHasResult();
        return versions;
    }

    public ModuleVersionResolveException getFailure() {
        assertHasResult();
        return failure;
    }

    public void listed(Collection<String> versions) {
        reset(State.Listed);
        this.versions = new LinkedHashSet<String>(versions);
        this.authoritative = true;
    }

    public void failed(ModuleVersionResolveException failure) {
        reset(State.Failed);
        this.failure = failure;
        this.authoritative = true;
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
}
