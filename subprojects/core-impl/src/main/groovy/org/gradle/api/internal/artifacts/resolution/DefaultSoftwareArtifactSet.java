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
package org.gradle.api.internal.artifacts.resolution;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.resolution.SoftwareArtifact;
import org.gradle.api.artifacts.resolution.SoftwareArtifactSet;
import org.gradle.util.CollectionUtils;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultSoftwareArtifactSet<T extends SoftwareArtifact> implements SoftwareArtifactSet<T> {
    private final Set<T> artifacts = new LinkedHashSet<T>();
    private final GradleException failure;

    public DefaultSoftwareArtifactSet(Iterable<? extends T> artifacts) {
        CollectionUtils.addAll(this.artifacts, artifacts);
        this.failure = null;
    }

    public DefaultSoftwareArtifactSet(GradleException failure) {
        this.failure = failure;
    }

    public Set<T> getArtifacts() throws GradleException {
        assertNoFailure();
        return artifacts;
    }

    public GradleException getFailure() {
        return failure;
    }

    private void assertNoFailure() {
        if (failure != null) {
            throw failure;
        }
    }

}
