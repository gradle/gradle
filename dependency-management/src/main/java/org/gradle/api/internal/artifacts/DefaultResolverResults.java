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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphValidationException;

public class DefaultResolverResults implements ResolverResults {
    private ResolvedConfiguration resolvedConfiguration;
    private ResolutionResult resolutionResult;
    private ResolveException fatalFailure;
    private ResolveException nonFatalFailure;
    private ResolvedLocalComponentsResult resolvedLocalComponentsResult;
    private Object artifactResolveState;
    private VisitedArtifactSet visitedArtifacts;

    @Override
    public boolean hasError() {
        if (fatalFailure != null || nonFatalFailure != null) {
            return true;
        }
        return resolvedConfiguration != null && resolvedConfiguration.hasError();
    }

    @Override
    public ResolvedConfiguration getResolvedConfiguration() {
        assertHasArtifactResult();
        return resolvedConfiguration;
    }

    @Override
    public ResolutionResult getResolutionResult() {
        assertHasGraphResult();
        return resolutionResult;
    }

    @Override
    public ResolvedLocalComponentsResult getResolvedLocalComponents() {
        assertHasGraphResult();
        return resolvedLocalComponentsResult;
    }

    @Override
    public VisitedArtifactSet getVisitedArtifacts() {
        assertHasVisitResult();
        return visitedArtifacts;
    }

    private void assertHasVisitResult() {
        maybeRethrowFatalError();
        if (visitedArtifacts == null) {
            throw new IllegalStateException("Resolution result has not been attached.");
        }
    }

    private void assertHasGraphResult() {
        maybeRethrowFatalError();
        if (resolvedLocalComponentsResult == null) {
            throw new IllegalStateException("Resolution result has not been attached.");
        }
    }

    private void maybeRethrowFatalError() {
        if (fatalFailure != null) {
            throw fatalFailure;
        }
    }

    private void maybeRethrowAnyError() {
        maybeRethrowFatalError();
        if (nonFatalFailure != null) {
            throw nonFatalFailure;
        }
    }

    private void assertHasArtifactResult() {
        if (resolvedConfiguration == null) {
            throw new IllegalStateException("Resolution artifacts have not been attached.");
        }
    }

    @Override
    public void graphResolved(ResolutionResult resolutionResult, ResolvedLocalComponentsResult resolvedLocalComponentsResult, VisitedArtifactSet visitedArtifacts) {
        this.resolutionResult = resolutionResult;
        this.resolvedLocalComponentsResult = resolvedLocalComponentsResult;
        this.visitedArtifacts = visitedArtifacts;
        this.fatalFailure = null;
    }

    @Override
    public void failed(ResolveException failure) {
        if (isNonFatalError(failure)) {
            nonFatalFailure = failure;
        } else {
            this.resolutionResult = null;
            this.resolvedLocalComponentsResult = null;
            this.fatalFailure = failure;
        }
    }

    private static boolean isNonFatalError(ResolveException failure) {
        return failure.getCause() instanceof GraphValidationException;
    }

    @Override
    public void artifactsResolved(ResolvedConfiguration resolvedConfiguration, VisitedArtifactSet visitedArtifacts) {
        this.resolvedConfiguration = resolvedConfiguration;
        this.visitedArtifacts = visitedArtifacts;
        this.artifactResolveState = null;
    }

    @Override
    public ResolveException consumeNonFatalFailure() {
        try {
            return nonFatalFailure;
        } finally {
            nonFatalFailure = null;
        }
    }

    @Override
    public Throwable getFailure() {
        if (fatalFailure != null) {
            return fatalFailure;
        }
        if (nonFatalFailure != null) {
            return nonFatalFailure;
        }
        return null;
    }

    @Override
    public void retainState(Object artifactResolveState) {
        this.artifactResolveState = artifactResolveState;
    }

    @Override
    public Object getArtifactResolveState() {
        maybeRethrowAnyError();
        return artifactResolveState;
    }
}
