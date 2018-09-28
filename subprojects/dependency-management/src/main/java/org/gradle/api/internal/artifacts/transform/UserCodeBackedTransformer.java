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
import org.gradle.api.artifacts.transform.TransformInvocationException;
import org.gradle.internal.hash.HashCode;

import java.io.File;
import java.util.List;

class UserCodeBackedTransformer implements ArtifactTransformer {
    private final HashCode inputsHash;
    private final TransformedFileCache transformedFileCache;
    private final TransformArtifactsAction transformAction;

    public UserCodeBackedTransformer(TransformArtifactsAction transformAction, HashCode inputHash, TransformedFileCache cache) {
        this.transformAction = transformAction;
        this.inputsHash = inputHash;
        this.transformedFileCache = cache;
    }

    @Override
    public List<File> transform(File input) {
        try {
            File absoluteFile = input.getAbsoluteFile();
            return transformedFileCache.getResult(absoluteFile, inputsHash, transformAction);
        } catch (Throwable t) {
            throw new TransformInvocationException(input, transformAction.getImplementationClass(), t);
        }
    }

    @Override
    public boolean hasCachedResult(File input) {
        return transformedFileCache.contains(input.getAbsoluteFile(), inputsHash);
    }

    @Override
    public String getDisplayName() {
        return transformAction.getDisplayName();
    }

    @Override
    public void visitLeafTransformers(Action<? super ArtifactTransformer> action) {
        action.execute(this);
    }

    @Override
    public String toString() {
        return String.format("%s@%s", transformAction.getDisplayName(), inputsHash);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UserCodeBackedTransformer that = (UserCodeBackedTransformer) o;
        return inputsHash.equals(that.inputsHash);
    }

    @Override
    public int hashCode() {
        return inputsHash.hashCode();
    }
}
