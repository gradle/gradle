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

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

class TransformArtifactOperation implements RunnableBuildOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformArtifactOperation.class);
    private final ComponentArtifactIdentifier artifactId;
    private final File file;
    private final ArtifactTransformation transform;
    private final ArtifactTransformListener transformListener;
    private Throwable failure;
    private List<File> result;

    TransformArtifactOperation(ComponentArtifactIdentifier artifactId, File file, ArtifactTransformation transform, ArtifactTransformListener transformListener) {
        this.artifactId = artifactId;
        this.file = file;
        this.transform = transform;
        this.transformListener = transformListener;
    }

    @Override
    public void run(@Nullable BuildOperationContext context) {
        boolean hasCachedResult = transform.hasCachedResult(file);
        if (!hasCachedResult) {
            transformListener.beforeTransform(transform, artifactId, file);
        }
        try {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Executing transform {} on artifact {}", transform.getDisplayName(), artifactId.getDisplayName());
            }
            result = transform.transform(file);
        } catch (Throwable t) {
            failure = t;
        }
        if (!hasCachedResult) {
            transformListener.afterTransform(transform, artifactId, file, failure);
        }
    }

    @Override
    public BuildOperationDescriptor.Builder description() {
        String displayName = "Transform " + artifactId.getDisplayName() + " with " + transform.getDisplayName();
        return BuildOperationDescriptor.displayName(displayName)
            .progressDisplayName(displayName)
            .operationType(BuildOperationCategory.UNCATEGORIZED);
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
