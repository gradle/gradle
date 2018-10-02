/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.collect.ImmutableList;

import java.io.File;

public class TransformationStep {
    private final TransformerRegistration transformerRegistration;
    private final TransformedFileCache transformedFileCache;

    public TransformationStep(TransformerRegistration transformerRegistration, TransformedFileCache transformedFileCache) {
        this.transformerRegistration = transformerRegistration;
        this.transformedFileCache = transformedFileCache;
    }

    public TransformationSubject transform(TransformationSubject toTransform) {
        try {
            ImmutableList.Builder<File> builder = ImmutableList.builder();
            for (File fileToTransform : toTransform.getFiles()) {
                builder.addAll(transformedFileCache.runTransformer(fileToTransform, transformerRegistration));
            }
            return new DefaultTransformationSubject(builder.build());
        } catch (Exception e) {
            return new DefaultTransformationSubject(e);
        }
    }
}
