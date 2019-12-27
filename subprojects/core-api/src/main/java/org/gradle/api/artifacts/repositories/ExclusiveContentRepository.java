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
package org.gradle.api.artifacts.repositories;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.internal.Factory;

/**
 * Describes one or more repositories which together constitute the only possible
 * source for an artifact, independently of the others.
 *
 * This means that if a repository declares an include, other repositories will
 * automatically exclude it.
 *
 * @since 6.2
 */
@Incubating
public interface ExclusiveContentRepository {
    /**
     * Declares the repository
     * @param repository the repository for which we declare exclusive content
     * @return this repository descriptor
     */
    ExclusiveContentRepository forRepository(Factory<? extends ArtifactRepository> repository);

    /**
     * Declares the repository
     * @param repositories the repositories for which we declare exclusive content
     * @return this repository descriptor
     */
    ExclusiveContentRepository forRepositories(ArtifactRepository... repositories);

    /**
     * Defines the content filter for this repository
     * @param config the configuration of the filter
     * @return this repository descriptor
     */
    ExclusiveContentRepository filter(Action<? super InclusiveRepositoryContentDescriptor> config);
}
