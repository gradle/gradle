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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ArtifactExclude;
import org.gradle.internal.component.model.IvyArtifactName;

final class DefaultModuleArtifactExclude implements ArtifactExclude {
    private final ModuleIdentifier moduleId;
    private final IvyArtifactName artifactName;
    private final int hashCode;

    static ArtifactExclude of(ModuleIdentifier id, IvyArtifactName artifact) {
        return new DefaultModuleArtifactExclude(id, artifact);
    }

    private DefaultModuleArtifactExclude(ModuleIdentifier moduleId, IvyArtifactName artifactName) {
        this.moduleId = moduleId;
        this.artifactName = artifactName;
        this.hashCode = 31 * moduleId.hashCode() + artifactName.hashCode();
    }

    @Override
    public IvyArtifactName getArtifact() {
        return artifactName;
    }

    @Override
    public boolean excludesArtifact(ModuleIdentifier module, IvyArtifactName artifactName) {
        return moduleId.equals(module) && this.artifactName.equals(artifactName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultModuleArtifactExclude that = (DefaultModuleArtifactExclude) o;

        if (hashCode != that.hashCode) {
            return false;
        }
        if (!moduleId.equals(that.moduleId)) {
            return false;
        }
        return artifactName.equals(that.artifactName);

    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "{ \"artifact\": { \"name\": \"" + artifactName + "\", \"module\" : \"" + moduleId + "\"} }";
    }
}
