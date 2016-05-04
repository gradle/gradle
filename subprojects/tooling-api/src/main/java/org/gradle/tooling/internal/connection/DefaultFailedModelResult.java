/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.connection;

import org.gradle.api.Nullable;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.connection.FailedModelResult;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.ProjectIdentifier;

public class DefaultFailedModelResult<T> implements FailedModelResult<T> {
    private final GradleConnectionException failure;
    private final BuildIdentifier buildIdentifier;
    private final ProjectIdentifier projectIdentifier;

    public DefaultFailedModelResult(BuildIdentifier buildIdentifier, GradleConnectionException failure) {
        this.failure = failure;
        this.buildIdentifier = buildIdentifier;
        this.projectIdentifier = null;
    }

    public DefaultFailedModelResult(ProjectIdentifier projectIdentifier, GradleConnectionException failure) {
        this.failure = failure;
        this.buildIdentifier = projectIdentifier.getBuildIdentifier();
        this.projectIdentifier = projectIdentifier;
    }

    @Override
    public T getModel() {
        throw failure;
    }

    @Override
    public GradleConnectionException getFailure() {
        return failure;
    }

    public BuildIdentifier getBuildIdentifier() {
        return buildIdentifier;
    }

    @Nullable
    public ProjectIdentifier getProjectIdentifier() {
        return projectIdentifier;
    }

    @Override
    public String toString() {
        return String.format("result={ failure=%s }", failure.getMessage());
    }
}
