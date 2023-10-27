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

import com.google.common.collect.ImmutableSet;
import org.gradle.internal.resolve.ModuleVersionResolveException;

import java.util.Collection;
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

    @Override
    public State getState() {
        return state;
    }

    @Override
    public boolean hasResult() {
        return state != State.Unknown;
    }

    @Override
    public Set<String> getVersions() throws ModuleVersionResolveException {
        assertHasResult();
        return versions;
    }

    @Override
    public ModuleVersionResolveException getFailure() {
        assertHasResult();
        return failure;
    }

    @Override
    public void listed(Collection<String> versions) {
        reset(State.Listed);
        this.versions = ImmutableSet.copyOf(versions);
        this.authoritative = true;
    }

    @Override
    public void failed(ModuleVersionResolveException failure) {
        reset(State.Failed);
        this.failure = failure;
        this.authoritative = true;
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
}
