/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.artifacts.repositories;

import org.gradle.internal.HasInternalProtocol;

/**
 * A repository for resolving and publishing artifacts.
 */
@HasInternalProtocol
public interface ArtifactRepository {
    /**
     * Returns the name for this repository. A name must be unique amongst a repository set. A default name is provided for the repository if none
     * is provided.
     *
     * <p>The name is used in logging output and error reporting to point to information related to this repository.
     *
     * @return The name.
     */
    String getName();

    /**
     * Sets the name for this repository.
     *
     * If this repository is to be added to an {@link org.gradle.api.artifacts.ArtifactRepositoryContainer}
     * (including {@link org.gradle.api.artifacts.dsl.RepositoryHandler}), its name should not be changed after it has
     * been added to the container. This capability has been deprecated and is scheduled to be removed in the next major
     * Gradle version.
     *
     * @param name The name. Must not be null.
     */
    void setName(String name);
}
