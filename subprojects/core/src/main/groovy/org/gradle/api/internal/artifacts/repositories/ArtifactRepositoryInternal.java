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
package org.gradle.api.internal.artifacts.repositories;

import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.artifacts.dsl.ArtifactRepository;

import java.util.Collection;

public interface ArtifactRepositoryInternal extends ArtifactRepository {
    /**
     * Creates the resolvers for this repository.
     *
     * @param resolvers The collection to add the resolvers for this repository.
     */
    void createResolvers(Collection<DependencyResolver> resolvers);
}
