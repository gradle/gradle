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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.FileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResult;

public class DefaultResolverResults implements ResolverResults {
    private ResolvedConfiguration resolvedConfiguration;
    private ArtifactResults artifactResults;
    private ResolutionResult resolutionResult;
    private ResolveException fatalFailure;
    private ResolvedLocalComponentsResult resolvedLocalComponentsResult;
    private Object artifactResolveState;
    private FileDependencyResults fileDependencyResults;

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
    public ArtifactResults getArtifactResults() {
        assertHasArtifacts();
        return artifactResults;
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
        assertHasLocalResult();
        return resolvedLocalComponentsResult;
    }

    @Override
    public FileDependencyResults getFileDependencies() {
        assertHasLocalResult();
        return fileDependencyResults;
    }

    private void assertHasLocalResult() {
        if (fatalFailure != null) {
            throw fatalFailure;
        }
        if (resolvedLocalComponentsResult == null) {
            throw new IllegalStateException("Resolution result has not been attached.");
        }
    }

    private void assertHasArtifacts() {
        if (resolvedConfiguration == null) {
            throw new IllegalStateException("Resolution artifacts have not been attached.");
        }
    }

    @Override
    public void graphResolved(ResolvedLocalComponentsResult resolvedLocalComponentsResult, FileDependencyResults fileDependencyResults) {
        this.fileDependencyResults = fileDependencyResults;
        this.resolvedLocalComponentsResult = resolvedLocalComponentsResult;
        this.resolutionResult = null;
        this.fatalFailure = null;
    }

    @Override
    public void graphResolved(ResolutionResult resolutionResult, ResolvedLocalComponentsResult resolvedLocalComponentsResult, FileDependencyResults fileDependencyResults) {
        this.resolutionResult = resolutionResult;
        this.resolvedLocalComponentsResult = resolvedLocalComponentsResult;
        this.fileDependencyResults = fileDependencyResults;
        this.fatalFailure = null;
    }

    @Override
    public void failed(ResolveException failure) {
        this.resolutionResult = null;
        this.resolvedLocalComponentsResult = null;
        this.fatalFailure = failure;
    }

    @Override
    public void artifactsResolved(ResolvedConfiguration resolvedConfiguration, ArtifactResults artifactResults) {
        this.resolvedConfiguration = resolvedConfiguration;
        this.artifactResults = artifactResults;
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
