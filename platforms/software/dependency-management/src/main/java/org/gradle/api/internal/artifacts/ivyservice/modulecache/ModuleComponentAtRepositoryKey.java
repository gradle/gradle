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

package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

public class ModuleComponentAtRepositoryKey {
    private final String repositoryId;
    private final ModuleComponentIdentifier componentId;
    private final int hashCode;

    ModuleComponentAtRepositoryKey(String repositoryId, ModuleComponentIdentifier componentId) {
        this.repositoryId = repositoryId;
        this.componentId = componentId;
        this.hashCode = 31 * repositoryId.hashCode() + componentId.hashCode();
    }

    @Override
    public String toString() {
        return repositoryId + "," + componentId;
    }

    public ModuleComponentIdentifier getComponentId() {
        return componentId;
    }

    public String getRepositoryId() {
        return repositoryId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ModuleComponentAtRepositoryKey)) {
            return false;
        }
        ModuleComponentAtRepositoryKey other = (ModuleComponentAtRepositoryKey) o;
        return hashCode == other.hashCode && repositoryId.equals(other.repositoryId) && componentId.equals(other.componentId);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
