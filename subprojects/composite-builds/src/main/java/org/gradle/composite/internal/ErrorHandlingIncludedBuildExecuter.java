/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.composite.internal;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.initialization.IncludedBuildExecuter;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.LocationAwareException;

class ErrorHandlingIncludedBuildExecuter implements IncludedBuildExecuter {
    private final IncludedBuildExecuter delegate;

    public ErrorHandlingIncludedBuildExecuter(IncludedBuildExecuter delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(final BuildIdentifier targetBuild, final Iterable<String> taskNames) {
        try {
            delegate.execute(targetBuild, taskNames);
        } catch (ReportedException e) {
            throw contextualizeFailure(targetBuild, e);
        }
    }

    private RuntimeException contextualizeFailure(BuildIdentifier buildId, ReportedException e) {
        if (e.getCause() instanceof LocationAwareException) {
            LocationAwareException lae = (LocationAwareException) e.getCause();
            IncludedBuildExecutionException wrappedCause = new IncludedBuildExecutionException("Failed to build artifacts for " + buildId, lae.getCause());
            LocationAwareException newLae = new LocationAwareException(wrappedCause, lae.getSourceDisplayName(), lae.getLineNumber());
            return new ReportedException(newLae);
        }
        return e;
    }

    @Contextual
    private static class IncludedBuildExecutionException extends GradleException {
        public IncludedBuildExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
