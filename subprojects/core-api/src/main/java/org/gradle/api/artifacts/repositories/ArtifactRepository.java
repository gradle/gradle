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

import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.attributes.AttributeContainer;
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
     * (including {@link org.gradle.api.artifacts.dsl.RepositoryHandler}), its name cannot be changed after it has
     * been added to the container.
     *
     * @param name The name. Must not be null.
     * @throws IllegalStateException If the name is set after it has been added to the container.
     */
    void setName(String name);

    void contentFilter(Action<? super ArtifactResolutionDetails> spec);

    @HasInternalProtocol
    interface ArtifactResolutionDetails {
        /**
         * The identifier of the module being looked for in this repository
         * @return the module identifier
         */
        ModuleIdentifier getId();

        /**
         * The attributes of the consumer looking for this module
         * @return the consumer attributes
         */
        AttributeContainer getConsumerAttributes();

        /**
         * The name of the consumer. Usually corresponds to the name of the configuration being
         * resolved.
         * @return the consumer name
         */
        String getConsumerName();

        /**
         * Declares that this artifact will not be found on this repository
         */
        void notFound();
    }
}
