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
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;

import static com.google.common.base.Objects.equal;

public class DefaultIvyArtifactName implements IvyArtifactName {
    private final String name;
    private final String type;
    private final String extension;
    private final String classifier;

    public static DefaultIvyArtifactName forPublishArtifact(PublishArtifact publishArtifact) {
        String name = publishArtifact.getName();
        if (name == null) {
            name = publishArtifact.getFile().getName();
        }
        String classifier = GUtil.elvis(publishArtifact.getClassifier(), null);
        return new DefaultIvyArtifactName(name, publishArtifact.getType(), publishArtifact.getExtension(), classifier);
    }

    public DefaultIvyArtifactName(String name, String type, @Nullable String extension) {
        this(name, type, extension, null);
    }

    public DefaultIvyArtifactName(String name, String type, @Nullable String extension, @Nullable String classifier) {
        this.name = name;
        this.type = type;
        this.extension = extension;
        this.classifier = classifier;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(name);
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
        return Objects.hashCode(name, type, extension, classifier);
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
        return equal(name, other.name)
            && equal(type, other.type)
            && equal(extension, other.extension)
            && equal(classifier, other.classifier);
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

    public String getClassifier() {
        return classifier;
    }
}
