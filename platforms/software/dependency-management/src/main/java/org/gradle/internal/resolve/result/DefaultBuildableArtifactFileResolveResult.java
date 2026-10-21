/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.internal.resolve.ArtifactNotFoundException;
import org.gradle.internal.resolve.ArtifactResolveException;
import org.jspecify.annotations.Nullable;

import java.io.File;

public class DefaultBuildableArtifactFileResolveResult extends DefaultResourceAwareResolveResult implements BuildableArtifactFileResolveResult {

    private @Nullable File result;
    private @Nullable ArtifactResolveException failure;
    private @Nullable ComponentArtifactIdentifier notFound;

    @Override
    public void resolved(File result) {
        this.result = result;
        this.failure = null;
        this.notFound = null;
    }

    @Override
    public void failed(ArtifactResolveException failure) {
        this.result = null;
        this.failure = failure;
        this.notFound = null;
    }

    @Override
    public void notFound(ComponentArtifactIdentifier artifact) {
        this.result = null;
        this.failure = null;
        this.notFound = artifact;
    }

    @Override
    public boolean hasResult() {
        return result != null || failure != null || notFound != null;
    }

    @Override
    public boolean isSuccessful() {
        return result != null;
    }

    @Override
    public boolean isNotFound() {
        return notFound != null;
    }

    @Override
    @Nullable
    public ArtifactResolveException getFailure() {
        assertHasResult();
        return failure;
    }

    @Override
    public ArtifactNotFoundException getNotFoundFailure() {
        if (notFound == null) {
            throw new IllegalStateException("The artifact was not marked as not found.");
        }
        return new ArtifactNotFoundException(notFound, getAttempted());
    }

    @Override
    public File getResult() throws ArtifactResolveException {
        assertHasResult();
        if (failure != null) {
            throw failure;
        }
        if (notFound != null) {
            throw getNotFoundFailure();
        }
        return result;
    }

    private void assertHasResult() {
        if (!hasResult()) {
            throw new IllegalStateException("No result has been specified.");
        }
    }

}
