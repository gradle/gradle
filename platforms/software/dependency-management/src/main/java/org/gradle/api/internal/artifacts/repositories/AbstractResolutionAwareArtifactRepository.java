/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.repositories.descriptor.RepositoryDescriptor;
import org.gradle.api.model.ObjectFactory;

public abstract class AbstractResolutionAwareArtifactRepository<T extends RepositoryDescriptor> extends AbstractArtifactRepository implements ResolutionAwareRepository {

    protected AbstractResolutionAwareArtifactRepository(ObjectFactory objectFactory, VersionParser versionParser) {
        super(objectFactory, versionParser);
    }

    @Override
    final public T getDescriptor() {
        // TODO: Gradle 9.0, check if we need caching
        //FIXME: should be cached, but we need to make sure it's invalidated when the repository changes
        //Currently, it's not possible to wire an action on property change
        return createDescriptor();
    }

    protected void invalidateDescriptor() {
    }

    protected abstract T createDescriptor();
}
