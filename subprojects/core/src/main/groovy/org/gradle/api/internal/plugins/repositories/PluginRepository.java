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

package org.gradle.api.internal.plugins.repositories;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * Represents a repository from which Gradle plugins can be resolved.
 */
@Incubating
@HasInternalProtocol
public interface PluginRepository {
    /**
     * Returns the name for this repository. A name must be unique amongst a repository set. A
     * default name is provided for the repository if none is provided.
     *
     * <p>The name is used in logging output and error reporting to point to information related to this repository.
     *
     * @return The name.
     */
    String getName();

    /**
     * Sets the name for this repository.
     *
     * If this repository is to be added to a
     * {@link org.gradle.api.internal.plugins.dsl.PluginRepositoryHandler}, its name should not be changed
     * after it has been added to the container. This capability has been deprecated and is
     * scheduled to be removed in the next major Gradle version.
     *
     * @param name The name. Must not be null.
     */
    void setName(String name);
}
