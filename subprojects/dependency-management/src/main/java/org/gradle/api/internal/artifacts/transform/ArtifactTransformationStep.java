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

import java.io.File;
import java.util.List;

class ArtifactTransformationStep implements ArtifactTransformation {
    private final TransformedFileCache transformedFileCache;
    private final TransformerRegistration transformerRegistration;

    public ArtifactTransformationStep(TransformerRegistration transformerRegistration, TransformedFileCache transformedFileCache) {
        this.transformerRegistration = transformerRegistration;
        this.transformedFileCache = transformedFileCache;
    }

    @Override
    public List<File> transform(File input) {
        return transformedFileCache.runTransformer(input, transformerRegistration);
    }

    @Override
    public boolean hasCachedResult(File input) {
        return transformedFileCache.contains(input.getAbsoluteFile(), transformerRegistration.getInputsHash());
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
