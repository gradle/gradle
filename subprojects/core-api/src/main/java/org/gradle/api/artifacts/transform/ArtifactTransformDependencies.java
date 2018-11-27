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

package org.gradle.api.artifacts.transform;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

import java.io.File;

/**
 * An injectable service that when injected into a {@link ArtifactTransform} can be used to access
 * the dependency artifacts of the artifact being transformed.
 *
 * @since 5.1
 */
@Incubating
@HasInternalProtocol
public interface ArtifactTransformDependencies {
    /**
     * Returns the dependency artifacts of the artifact being transformed.
     * The order of the files match that of the dependencies in the source artifact view.
     */
    Iterable<File> getFiles();
}
