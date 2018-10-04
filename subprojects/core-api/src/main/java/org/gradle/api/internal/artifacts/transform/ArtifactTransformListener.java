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

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;

import java.io.File;
import javax.annotation.Nullable;

public interface ArtifactTransformListener {
    /**
     * This method is called immediately before a transform is executed.
     */
    void beforeTransform(ArtifactTransformation transform, @Nullable ComponentArtifactIdentifier artifactId, File file);

    /**
     * This method is call immediately after a transform has been executed.
     */
    void afterTransform(ArtifactTransformation transform, @Nullable ComponentArtifactIdentifier artifactId, File file, @Nullable Throwable failure);
}
