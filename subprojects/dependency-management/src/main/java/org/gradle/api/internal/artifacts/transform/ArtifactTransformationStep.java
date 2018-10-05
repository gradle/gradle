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

import org.gradle.api.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class ArtifactTransformationStep implements ArtifactTransformation {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactTransformationStep.class);

    private final TransformedFileCache transformedFileCache;
    private final TransformerRegistration transformerRegistration;
    private final ArtifactTransformListener artifactTransformListener;

    public ArtifactTransformationStep(TransformerRegistration transformerRegistration, TransformedFileCache transformedFileCache, ArtifactTransformListener artifactTransformListener) {
        this.transformerRegistration = transformerRegistration;
        this.transformedFileCache = transformedFileCache;
        this.artifactTransformListener = artifactTransformListener;
    }

    @Override
    public TransformationSubject transform(TransformationSubject subjectToTransform) {
        if (subjectToTransform.getFailure() != null) {
            return subjectToTransform;
        }
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Executing transform {} on {}", transformerRegistration.getDisplayName(), subjectToTransform.getDisplayName());
            }
        List<File> result = new ArrayList<File>();
        for (File file : subjectToTransform.getFiles()) {
            boolean hasCachedResult = hasCachedResultForSingleFile(file);
            if (!hasCachedResult) {
                artifactTransformListener.beforeTransform(transformerRegistration, subjectToTransform);
            }
            try {
                result.addAll(transformedFileCache.runTransformer(file, transformerRegistration));
            } catch (Throwable t) {
                return new DefaultTransformationSubject(subjectToTransform, t);
            } finally {
                if (!hasCachedResult) {
                    artifactTransformListener.afterTransform(transformerRegistration, subjectToTransform);
                }
            }
        }
        return new DefaultTransformationSubject(subjectToTransform, result);
    }

    public boolean hasCachedResultForSingleFile(File input) {
        return transformedFileCache.contains(input.getAbsoluteFile(), transformerRegistration.getInputsHash());
    }

    @Override
    public boolean hasCachedResult(TransformationSubject subject) {
        if (subject.getFailure() != null) {
            return true;
        }
        for (File file : subject.getFiles()) {
            if (!hasCachedResultForSingleFile(file)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getDisplayName() {
        return transformerRegistration.getDisplayName();
    }

    @Override
    public void visitTransformationSteps(Action<? super ArtifactTransformation> action) {
        action.execute(this);
    }

    @Override
    public String toString() {
        return String.format("%s@%s", transformerRegistration.getDisplayName(), transformerRegistration.getInputsHash());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ArtifactTransformationStep that = (ArtifactTransformationStep) o;
        return transformerRegistration.equals(that.transformerRegistration);
    }

    @Override
    public int hashCode() {
        return transformerRegistration.hashCode();
    }
}
