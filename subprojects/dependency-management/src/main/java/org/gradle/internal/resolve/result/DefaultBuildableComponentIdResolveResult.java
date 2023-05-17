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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.RejectedVersion;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

public class DefaultBuildableComponentIdResolveResult extends DefaultResourceAwareResolveResult implements BuildableComponentIdResolveResult {
    private ModuleVersionResolveException failure;
    private ComponentGraphResolveState state;
    private ComponentGraphSpecificResolveState graphState;
    private ComponentIdentifier id;
    private ModuleVersionIdentifier moduleVersionId;
    private boolean rejected;
    private ImmutableSet.Builder<String> unmatchedVersions;
    private ImmutableSet.Builder<RejectedVersion> rejections;
    private Object mark;

    @Override
    public boolean hasResult() {
        return id != null || failure != null;
    }

    @Override
    public ModuleVersionResolveException getFailure() {
        return failure;
    }

    @Override
    public ComponentIdentifier getId() {
        assertResolved();
        return id;
    }

    @Override
    public ModuleVersionIdentifier getModuleVersionId() {
        assertResolved();
        return moduleVersionId;
    }

    @Override
    public ComponentGraphResolveState getState() {
        assertResolved();
        return state;
    }

    @Nullable
    @Override
    public ComponentGraphSpecificResolveState getGraphState() {
        assertResolved();
        return graphState;
    }

    @Override
    public boolean isRejected() {
        return rejected;
    }

    @Override
    public void resolved(ComponentIdentifier id, ModuleVersionIdentifier moduleVersionIdentifier) {
        reset();
        this.id = id;
        this.moduleVersionId = moduleVersionIdentifier;
    }

    @Override
    public void rejected(ComponentIdentifier id, ModuleVersionIdentifier moduleVersionIdentifier) {
        resolved(id, moduleVersionIdentifier);
        rejected = true;
    }

    @Override
    public void resolved(ComponentGraphResolveState state, ComponentGraphSpecificResolveState graphState) {
        resolved(state.getId(), state.getMetadata().getModuleVersionId());
        this.state = state;
        this.graphState = graphState;
    }

    @Override
    public void failed(ModuleVersionResolveException failure) {
        reset();
        this.failure = failure;
    }

    @Override
    public void unmatched(Collection<String> unmatchedVersions) {
        if (unmatchedVersions.isEmpty()) {
            return;
        }
        if (this.unmatchedVersions == null) {
            this.unmatchedVersions = new ImmutableSet.Builder<>();
        }
        this.unmatchedVersions.addAll(unmatchedVersions);
    }

    @Override
    public void rejections(Collection<RejectedVersion> rejections) {
        if (rejections.isEmpty()) {
            return;
        }
        if (this.rejections == null) {
            this.rejections = new ImmutableSet.Builder<>();
        }
        this.rejections.addAll(rejections);
    }

    @Override
    public Collection<String> getUnmatchedVersions() {
        return safeBuild(unmatchedVersions);
    }

    @Override
    public Collection<RejectedVersion> getRejectedVersions() {
        return safeBuild(rejections);
    }

    @Override
    public boolean mark(Object o) {
        if (mark == o) {
            return false;
        }
        mark = o;
        return true;
    }

    private static <T> Collection<T> safeBuild(ImmutableSet.Builder<T> builder) {
        if (builder == null) {
            return Collections.emptyList();
        }
        return builder.build();
    }

    private void assertResolved() {
        if (failure != null) {
            throw failure;
        }
        if (id == null) {
            throw new IllegalStateException("Not resolved.");
        }
    }

    private void reset() {
        failure = null;
        state = null;
        id = null;
        moduleVersionId = null;
        rejected = false;
    }
}
