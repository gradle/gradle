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

package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResult;

public class DefaultResolverResults implements ResolverResults {
    private ResolvedConfiguration resolvedConfiguration;
    private ResolutionResult resolutionResult;
    private ResolveException fatalFailure;
    private ResolvedLocalComponentsResult resolvedLocalComponentsResult;
    private Object artifactResolveState;

    @Override
    public boolean hasError() {
        if (fatalFailure != null) {
            return true;
        }
        if (resolvedConfiguration != null && resolvedConfiguration.hasError()) {
            return true;
        }
        return false;
    }

    @Override
    public ResolvedConfiguration getResolvedConfiguration() {
        assertHasArtifacts();
        return resolvedConfiguration;
    }

    @Override
    public ResolutionResult getResolutionResult() {
        if (fatalFailure != null) {
            throw fatalFailure;
        }
        if (resolutionResult == null) {
            throw new IllegalStateException("Resolution result has not been attached.");
        }
        return resolutionResult;
    }

    @Override
    public ResolvedLocalComponentsResult getResolvedLocalComponents() {
        if (fatalFailure != null) {
            throw fatalFailure;
        }
        if (resolvedLocalComponentsResult == null) {
            throw new IllegalStateException("Resolution result has not been attached.");
        }
        return resolvedLocalComponentsResult;
    }

    private void assertHasArtifacts() {
        if (resolvedConfiguration == null) {
            throw new IllegalStateException("Resolution artifacts have not been attached.");
        }
    }

    @Override
    public void resolved(ResolvedLocalComponentsResult resolvedLocalComponentsResult) {
        this.resolutionResult = null;
        this.resolvedLocalComponentsResult = resolvedLocalComponentsResult;
        this.fatalFailure = null;
    }

    @Override
    public void resolved(ResolutionResult resolutionResult, ResolvedLocalComponentsResult resolvedLocalComponentsResult) {
        this.resolutionResult = resolutionResult;
        this.resolvedLocalComponentsResult = resolvedLocalComponentsResult;
        this.fatalFailure = null;
    }

    @Override
    public void failed(ResolveException failure) {
        this.resolutionResult = null;
        this.resolvedLocalComponentsResult = null;
        this.fatalFailure = failure;
    }

    @Override
    public void withResolvedConfiguration(ResolvedConfiguration resolvedConfiguration) {
        this.resolvedConfiguration = resolvedConfiguration;
        this.artifactResolveState = null;
    }

    @Override
    public void retainState(Object artifactResolveState) {
        this.artifactResolveState = artifactResolveState;
    }

    @Override
    public Object getArtifactResolveState() {
        return artifactResolveState;
    }
}
