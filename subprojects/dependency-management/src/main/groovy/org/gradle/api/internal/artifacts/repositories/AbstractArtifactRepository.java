/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.artifacts.repositories.ArtifactRepository;

public abstract class AbstractArtifactRepository implements ArtifactRepositoryInternal {

    private String name;
    private boolean isPartOfContainer;

    public void onAddToContainer(NamedDomainObjectCollection<ArtifactRepository> container) {
        isPartOfContainer = true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (isPartOfContainer) {
            throw new IllegalStateException("The name of an ArtifactRepository cannot be changed after it has been added to a repository container. You should set the name when creating the repository.");
        }
        this.name = name;
    }
}
