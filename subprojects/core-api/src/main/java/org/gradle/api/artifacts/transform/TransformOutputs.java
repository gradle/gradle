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
 *     The registered output {@link File}s will appear in the transformed variant in the order they were registered.
 * </p>
 *
 * @since 5.3
 */
@Incubating
@HasInternalProtocol
public interface TransformOutputs {
    /**
     * Registers an output directory.
     * For a relative path, a location for the output directory is provided.
     *
     * @param path path of the output directory
     * @return determined location of the output
     */
    File dir(Object path);

    /**
     * Registers an output file.
     * For a relative path, a location for the output file is provided.
     *
     * @param path path of the output directory
     * @return determined location of the output
     */
    File file(Object path);
}
