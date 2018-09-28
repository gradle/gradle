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
import org.gradle.api.Describable;

import java.io.File;
import java.util.List;

/**
 * The internal API equivalent of {@link org.gradle.api.artifacts.transform.ArtifactTransform}, which is also aware of our cache infrastructure.
 */
public interface ArtifactTransformer extends Describable {

    /**
     * Transforms the given input file. May call the underlying user-provided transform or retrieve a cached value.
     */
    List<File> transform(File input);

    /**
     * Returns true if there is a cached result in memory, meaning that a call to {@link #transform(File)} will be fast.
     */
    boolean hasCachedResult(File input);

    void visitLeafTransformers(Action<? super ArtifactTransformer> action);
}
