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
package org.gradle.api.artifacts.resolution;

import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.Nullable;

// TODO:DAZ Separate 'result' interfaces out of the core component interfaces.
/**
 * A set of artifacts of a particular type for a software component.
 *
 * @since 1.12
 */
@Incubating
public interface SoftwareArtifactSet<T extends SoftwareArtifact> {
    /**
     * The artifacts in this set. If resolving the artifact set caused a failure, that failure will be rethrown.
     *
     * @return the set of artifacts
     */
    Iterable<T> getArtifacts() throws GradleException;

    /**
     * Returns the failure that occurred when the artifact set was resolved, or {@code null} if no failure occurred.
     *
     * @return the failure that occurred when the artifact set was resolved, or {@code null} if no failure occurred
     */
    @Nullable
    GradleException getFailure();
}
