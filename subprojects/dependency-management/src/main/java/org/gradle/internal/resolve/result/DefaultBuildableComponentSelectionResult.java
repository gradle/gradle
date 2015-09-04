/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.Nullable;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.resolve.ModuleVersionResolveException;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultBuildableComponentSelectionResult implements BuildableComponentSelectionResult {
    private State state = State.Unknown;
    private ModuleComponentIdentifier moduleComponentIdentifier;
    private ModuleVersionResolveException failure;
    private final Set<String> unmatchedVersions = new LinkedHashSet<String>();
    private final Set<String> rejectedVersions = new LinkedHashSet<String>();

    public void matches(ModuleComponentIdentifier moduleComponentIdentifier) {
        setChosenComponentWithReason(State.Match, moduleComponentIdentifier);
    }

    public void noMatchFound() {
        setChosenComponentWithReason(State.NoMatch, null);
    }

    private void setChosenComponentWithReason(State state, ModuleComponentIdentifier moduleComponentIdentifier) {
        this.state = state;
        this.moduleComponentIdentifier = moduleComponentIdentifier;
        this.failure = null;
    }

    public State getState() {
        return state;
    }

    public ModuleComponentIdentifier getMatch() {
        if (state != State.Match) {
            throw new IllegalStateException("This result has not been resolved.");
        }
        return moduleComponentIdentifier;
    }

    @Override
    public boolean hasResult() {
        return state != State.Unknown;
    }

    @Nullable
    @Override
    public ModuleVersionResolveException getFailure() {
        if (state == State.Unknown) {
            throw new IllegalStateException("This result has not been resolved.");
        }
        return failure;
    }

    @Override
    public void failed(ModuleVersionResolveException failure) {
        this.moduleComponentIdentifier = null;
        this.failure = failure;
        this.state = State.Failed;
    }

    public Set<String> getUnmatchedVersions() {
        return unmatchedVersions;
    }

    @Override
    public void notMatched(String candidateVersion) {
        unmatchedVersions.add(candidateVersion);
    }

    public Set<String> getRejectedVersions() {
        return rejectedVersions;
    }

    @Override
    public void rejected(String version) {
        rejectedVersions.add(version);
    }
}
