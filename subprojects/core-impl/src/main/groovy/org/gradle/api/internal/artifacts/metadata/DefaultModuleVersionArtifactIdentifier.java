/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.metadata;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;

import java.util.Map;

public class DefaultModuleVersionArtifactIdentifier implements ModuleVersionArtifactIdentifier {
    private final ModuleVersionIdentifier moduleVersionIdentifier;
    private final IvyArtifactName name;

    public DefaultModuleVersionArtifactIdentifier(ModuleVersionIdentifier moduleVersionIdentifier, Artifact artifact) {
        this.moduleVersionIdentifier = moduleVersionIdentifier;
        this.name = new DefaultIvyArtifactName(artifact);
    }

    public DefaultModuleVersionArtifactIdentifier(ModuleVersionIdentifier moduleVersionIdentifier, String name, String type, @Nullable String extension, Map<String, String> attributes) {
        this.moduleVersionIdentifier = moduleVersionIdentifier;
        this.name = new DefaultIvyArtifactName(name, type, extension, attributes);
    }

    public String getDisplayName() {
        return String.format("%s:%s", moduleVersionIdentifier, name);
    }

    public IvyArtifactName getName() {
        return name;
    }

    public ModuleVersionIdentifier getModuleVersionIdentifier() {
        return moduleVersionIdentifier;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public int hashCode() {
        return moduleVersionIdentifier.hashCode() ^ name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        DefaultModuleVersionArtifactIdentifier other = (DefaultModuleVersionArtifactIdentifier) obj;
        return other.moduleVersionIdentifier.equals(moduleVersionIdentifier)
                && other.name.equals(name);
    }
}
