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

import org.gradle.api.Describable;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformDependencies;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.hash.HashCode;

import java.io.File;
import java.util.List;

/**
 * The actual code which needs to be executed to transform a file.
 *
 * This encapsulates the public interface {@link ArtifactTransform} into an internal type.
 */
public interface Transformer extends Describable {
    Class<? extends ArtifactTransform> getImplementationClass();

    ImmutableAttributes getFromAttributes();

    /**
     * Whether the transformer requires dependencies of the transformed artifact to be injected.
     */
    boolean requiresDependencies();

    List<File> transform(File primaryInput, File outputDir, ArtifactTransformDependencies dependencies);

    /**
     * The hash of the secondary inputs of the transformer.
     *
     * This includes the parameters and the implementation.
     */
    HashCode getSecondaryInputHash();
}
