/*
 * Copyright 2019 the original author or authors.
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

/**
 * Interface for artifact transform actions.
 *
 * <p>Implementations must provide a public constructor.</p>
 *
 * <p>Implementations can receive parameters by using annotated abstract getter methods.</p>
 *
 * <p>A property annotated with {@link TransformParameters} will receive the object provided by {@link ParameterizedArtifactTransformSpec#getParameters()}.
 *
 * <p>A property annotated with {@link InputArtifact} will receive the <em>input artifact</em> location, which is the file or directory that the transform should be applied to.
 *
 * <p>A property annotated with {@link InputArtifactDependencies} will receive the <em>dependencies</em> of its input artifact.
 *
 * @since 5.3
 */
@Incubating
public interface TransformAction {
    /**
     * Executes the transform.
     *
     * @param outputs Receives the outputs of the transform.
     */
    void transform(ArtifactTransformOutputs outputs);
}
