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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

class TransformArtifactOperation implements RunnableBuildOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformArtifactOperation.class);
    private final ResolvableArtifact artifact;
    private final ArtifactTransformer transform;
    private Throwable failure;
    private List<File> result;

    TransformArtifactOperation(ResolvableArtifact artifact, ArtifactTransformer transform) {
        this.artifact = artifact;
        this.transform = transform;
    }

    @Override
    public void run(@Nullable BuildOperationContext context) {
        try {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Executing transform {} on artifact {}", transform.getDisplayName(), artifact.getId().getDisplayName());
            }
            result = transform.transform(artifact.getFile());
        } catch (Throwable t) {
            failure = t;
        }
    }

    @Override
    public BuildOperationDescriptor.Builder description() {
        return BuildOperationDescriptor.displayName("Apply " + transform.getDisplayName() + " to " + artifact);
    }

    @Nullable
    public Throwable getFailure() {
        return failure;
    }

    @Nullable
    public List<File> getResult() {
        return result;
    }
}
