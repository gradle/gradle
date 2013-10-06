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

import com.google.common.base.Objects;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.util.GUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DefaultModuleVersionArtifactIdentifier implements ModuleVersionArtifactIdentifier {
    private final ModuleVersionIdentifier moduleVersionIdentifier;
    private final String name;
    private final String type;
    private final String extension;
    private final Map<String, String> attributes;

    public DefaultModuleVersionArtifactIdentifier(ModuleVersionIdentifier moduleVersionIdentifier, Artifact artifact) {
        this(moduleVersionIdentifier, artifact.getName(), artifact.getType(), artifact.getExt(), artifact.getQualifiedExtraAttributes());
    }

    public DefaultModuleVersionArtifactIdentifier(ModuleVersionIdentifier moduleVersionIdentifier, String name, String type, @Nullable String extension, Map<String, String> attributes) {
        this.moduleVersionIdentifier = moduleVersionIdentifier;
        this.name = name;
        this.type = type;
        this.extension = extension;
        this.attributes = attributes.isEmpty() ? Collections.<String, String>emptyMap() : new HashMap<String, String>(attributes);
    }

    public String getDisplayName() {
        StringBuilder result = new StringBuilder();
        result.append(moduleVersionIdentifier);
        result.append(":");
        result.append(name);
        String classifier = attributes.get(Dependency.CLASSIFIER);
        if (GUtil.isTrue(classifier)) {
            result.append("-");
            result.append(classifier);
        }
        if (GUtil.isTrue(extension)) {
            result.append(".");
            result.append(extension);
        }

        return result.toString();
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public ModuleVersionIdentifier getModuleVersionIdentifier() {
        return moduleVersionIdentifier;
    }

    @Nullable
    public String getExtension() {
        return extension;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public int hashCode() {
        return moduleVersionIdentifier.hashCode() ^ name.hashCode() ^ type.hashCode() ^ (extension == null ? 0 : extension.hashCode()) ^ attributes.hashCode();
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
                && other.name.equals(name)
                && other.type.equals(type)
                && Objects.equal(other.extension, extension)
                && other.attributes.equals(attributes);
    }
}
