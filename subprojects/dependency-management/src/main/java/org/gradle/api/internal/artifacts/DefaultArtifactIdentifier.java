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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

import javax.annotation.Nullable;
import java.util.Objects;

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId;

public class DefaultArtifactIdentifier implements ArtifactIdentifier {
    private final ModuleVersionIdentifier moduleVersionIdentifier;
    private final IvyArtifactName name;

    public DefaultArtifactIdentifier(ModuleVersionIdentifier moduleVersionIdentifier, String name, String type, @Nullable String extension, @Nullable String classifier) {
        this.moduleVersionIdentifier = moduleVersionIdentifier;
        this.name = new DefaultIvyArtifactName(name, type, extension, classifier);
    }

    public DefaultArtifactIdentifier(DefaultModuleComponentArtifactIdentifier id) {
        this.moduleVersionIdentifier = newId(id.getComponentIdentifier());
        this.name = id.getName();
    }

    @Override
    public ModuleVersionIdentifier getModuleVersionIdentifier() {
        return moduleVersionIdentifier;
    }

    @Override
    public String getName() {
        return name.getName();
    }

    @Override
    public String getType() {
        return name.getType();
    }

    @Nullable
    @Override
    public String getExtension() {
        return name.getExtension();
    }

    @Nullable
    @Override
    public String getClassifier() {
        return name.getClassifier();
    }

    @Override
    public String toString() {
        return String.format("module: %s, name: %s", moduleVersionIdentifier, name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultArtifactIdentifier)) {
            return false;
        }

        DefaultArtifactIdentifier that = (DefaultArtifactIdentifier) o;

        if (!Objects.equals(moduleVersionIdentifier, that.moduleVersionIdentifier)) {
            return false;
        }
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        int result = moduleVersionIdentifier != null ? moduleVersionIdentifier.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }
}
