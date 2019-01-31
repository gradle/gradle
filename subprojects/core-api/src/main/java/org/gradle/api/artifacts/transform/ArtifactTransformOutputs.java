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
 * <p>The order in which the methods on this interface are called is important, since the output of a transform is ordered.</p>
 *
 * @since 5.3
 */
@Incubating
@HasInternalProtocol
public interface ArtifactTransformOutputs {
    /**
     * Registers an output file in the workspace of the transform.
     *
     * <p>The order of calls to this method is retained in the result of the transform.</p>
     *
     * @param relativePath relative path of the output to the provided workspace
     * @return determined location of the output
     */
    File registerOutput(String relativePath);

    /**
     * Registers an output file of the transform.
     *
     * <p>This method needs be used when part of the {@link PrimaryInput} is the output of the transform.</p>
     *
     * @param output the output
     */
    void registerOutputFile(File output);

    /**
     * Registers the provided workspace as the output of the transform.
     *
     * @return location of the workspace directory
     */
    File registerWorkspaceAsOutputDirectory();
}
