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

import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.artifacts.repositories.descriptor.RepositoryDescriptor;
import org.gradle.api.model.ObjectFactory;

public abstract class AbstractResolutionAwareArtifactRepository extends AbstractArtifactRepository implements ResolutionAwareRepository {
    protected AbstractResolutionAwareArtifactRepository(ObjectFactory objectFactory) {
        super(objectFactory, null);
    }
    protected AbstractResolutionAwareArtifactRepository(ObjectFactory objectFactory, FeaturePreviews featurePreviews) {
        super(objectFactory, featurePreviews);
    }

    private RepositoryDescriptor descriptor;

    @Override
    final public RepositoryDescriptor getDescriptor() {
        if (descriptor == null) {
            descriptor = createDescriptor();
        }

        return descriptor;
    }

    protected void invalidateDescriptor() {
        descriptor = null;
    }

    protected abstract RepositoryDescriptor createDescriptor();

    @Override
    public void setName(String name) {
        invalidateDescriptor();
        super.setName(name);
    }
}
