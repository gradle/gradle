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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ArtifactNotFoundException;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ArtifactResolveException;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactIdentifier;

import java.io.File;

public class DefaultBuildableArtifactResolveResult implements BuildableArtifactResolveResult {
    private ArtifactResolveException failure;
    private File file;

    public void failed(ArtifactResolveException failure) {
        this.failure = failure;
    }

    public void resolved(File file) {
        this.file = file;
    }

    public void notFound(ComponentArtifactIdentifier artifact) {
        failed(new ArtifactNotFoundException(artifact));
    }

    public ArtifactResolveException getFailure() {
        assertHasResult();
        return failure;
    }

    public File getFile() throws ArtifactResolveException {
        assertResolved();
        return file;
    }

    private void assertResolved() {
        assertHasResult();
        if (failure != null) {
            throw failure;
        }
    }

    private void assertHasResult() {
        if (failure == null && file == null) {
            throw new IllegalStateException("No result has been specified.");
        }
    }
}
