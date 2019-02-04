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
import org.gradle.internal.HasInternalProtocol;

import java.io.File;

/**
 * The outputs of the artifact transform.
 *
 * <p>
 *     The final output of the artifact transform is an ordered list of {@link File}s.
 *     In the variant produced by this transform, each artifact will be replaced by the final output of the transform executed on the artifact.
 *     This is why the order of calls to output registering methods on this interface is important.
 * </p>
 *
 * @since 5.3
 */
@Incubating
@HasInternalProtocol
public interface ArtifactTransformOutputs {
    /**
     * Registers an output file or directory in the workspace of the transform.
     *
     * @param relativePath relative path of the output to the provided workspace
     * @return determined location of the output
     */
    File registerOutput(String relativePath);

    /**
     * Registers an output file or directory of the transform.
     *
     * <p>This method can be used when the {@link PrimaryInput} is one of the outputs of the transform.</p>
     *
     * @param output the output
     */
    void registerOutput(File output);

    /**
     * Registers the provided workspace as the output of the transform.
     *
     * @return location of the workspace directory
     */
    File registerWorkspaceAsOutput();
}
