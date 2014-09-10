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

import org.gradle.internal.resolve.ArtifactResolveException;
import org.gradle.internal.component.model.ComponentArtifactMetaData;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultBuildableArtifactSetResolveResult implements BuildableArtifactSetResolveResult {
    private ArtifactResolveException failure;
    private Set<ComponentArtifactMetaData> artifacts;

    public void resolved(Collection<? extends ComponentArtifactMetaData> artifacts) {
        this.artifacts = new LinkedHashSet<ComponentArtifactMetaData>(artifacts);
    }

    public void failed(ArtifactResolveException failure) {
        this.failure = failure;
    }

    public boolean hasResult() {
        return artifacts != null || failure != null;
    }

    public ArtifactResolveException getFailure() {
        assertHasResult();
        return failure;
    }

    public Set<ComponentArtifactMetaData> getArtifacts() {
        assertResolved();
        return artifacts;
    }

    private void assertResolved() {
        assertHasResult();
        if (failure != null) {
            throw failure;
        }
    }

    private void assertHasResult() {
        if (!hasResult()) {
            throw new IllegalStateException("No result has been specified.");
        }
    }

}
