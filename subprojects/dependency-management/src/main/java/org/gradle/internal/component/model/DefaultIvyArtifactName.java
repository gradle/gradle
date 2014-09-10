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

package org.gradle.internal.component.model;

import com.google.common.base.Objects;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.api.Nullable;
import org.gradle.util.GUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DefaultIvyArtifactName implements IvyArtifactName {
    private static final String CLASSIFIER = "classifier";
    private final String name;
    private final String type;
    private final String extension;
    private final Map<String, String> attributes;

    public DefaultIvyArtifactName(Artifact a) {
        this(a.getName(), a.getType(), a.getExt(), a.getAttributes());
    }

    public DefaultIvyArtifactName(String name, String type, @Nullable String extension, Map<String, String> attributes) {
        this.name = name;
        this.type = type;
        this.extension = extension;
        if (attributes.isEmpty()) {
            this.attributes = Collections.emptyMap();
        } else {
            this.attributes = new HashMap<String, String>();
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                if (GUtil.isTrue(entry.getValue())) {
                    this.attributes.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    public DefaultIvyArtifactName(String name, String type, @Nullable String extension) {
        this.name = name;
        this.type = type;
        this.extension = extension;
        this.attributes = Collections.emptyMap();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(name);
        String classifier = attributes.get(CLASSIFIER);
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

    @Override
    public int hashCode() {
        return name.hashCode() ^ type.hashCode() ^ (extension == null ? 0 : extension.hashCode()) ^ attributes.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        DefaultIvyArtifactName other = (DefaultIvyArtifactName) obj;
        return other.name.equals(name)
                && other.type.equals(type)
                && Objects.equal(other.extension, extension)
                && other.attributes.equals(attributes);
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getExtension() {
        return extension;
    }

    @Nullable
    public String getClassifier() {
        return attributes.get(CLASSIFIER);
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }
}
