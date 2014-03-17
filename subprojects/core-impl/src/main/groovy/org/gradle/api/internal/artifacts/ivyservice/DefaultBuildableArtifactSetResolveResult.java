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
package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ArtifactResolveException;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultBuildableArtifactSetResolveResult implements BuildableArtifactSetResolveResult {
    private ArtifactResolveException failure;
    private Set<ModuleVersionArtifactMetaData> artifacts;

    public void resolved(Collection<? extends ModuleVersionArtifactMetaData> artifacts) {
        this.artifacts = new LinkedHashSet<ModuleVersionArtifactMetaData>(artifacts);
    }

    public void failed(ArtifactResolveException failure) {
        this.failure = failure;
    }

    public Set<ModuleVersionArtifactMetaData> getResults() {
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
        if (failure == null && artifacts == null) {
            throw new IllegalStateException("No result has been specified.");
        }
    }

}
