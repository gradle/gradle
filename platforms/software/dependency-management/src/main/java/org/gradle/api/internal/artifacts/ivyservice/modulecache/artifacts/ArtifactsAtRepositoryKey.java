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
package org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts;

import org.gradle.api.artifacts.component.ComponentIdentifier;

class ArtifactsAtRepositoryKey {
    final String repositoryId;
    final ComponentIdentifier componentId;
    final String context;

    ArtifactsAtRepositoryKey(String repositoryId, ComponentIdentifier componentId, String context) {
        this.repositoryId = repositoryId;
        this.componentId = componentId;
        this.context = context;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ArtifactsAtRepositoryKey)) {
            return false;
        }

        ArtifactsAtRepositoryKey that = (ArtifactsAtRepositoryKey) o;
        return repositoryId.equals(that.repositoryId) && componentId.equals(that.componentId) && context.equals(that.context);
    }

    @Override
    public int hashCode() {
        int result = repositoryId.hashCode();
        result = 31 * result + componentId.hashCode();
        result = 31 * result + context.hashCode();
        return result;
    }
}
