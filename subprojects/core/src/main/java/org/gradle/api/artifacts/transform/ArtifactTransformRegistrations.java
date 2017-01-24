/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * Registry for artifact transforms.
 *
 * @since 3.4
 */
@Incubating
@HasInternalProtocol
public interface ArtifactTransformRegistrations {

    /**
     * Register an artifact transformation.
     *
     * @param type implementation type of the artifact transformation
     * @param config a configuration action
     *
     * @see ArtifactTransform
     */
    @Incubating
    void registerTransform(Class<? extends ArtifactTransform> type, Action<? super ArtifactTransform> config);
}
