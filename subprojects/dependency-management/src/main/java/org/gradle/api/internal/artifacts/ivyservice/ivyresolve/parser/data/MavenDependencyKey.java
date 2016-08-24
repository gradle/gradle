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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data;

import com.google.common.base.Objects;

public class MavenDependencyKey {
    private static final String KEY_SEPARATOR = ":";
    private final String groupId;
    private final String artifactId;
    private final String type;
    private final String classifier;

    public MavenDependencyKey(String groupId, String artifactId, String type, String classifier) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.type = type;
        this.classifier = classifier;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getType() {
        return type;
    }

    public String getClassifier() {
        return classifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MavenDependencyKey that = (MavenDependencyKey) o;
        return Objects.equal(groupId, that.groupId)
            && Objects.equal(artifactId, that.artifactId)
            && Objects.equal(classifier, that.classifier)
            && Objects.equal(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(groupId, artifactId, classifier, type);
    }

    @Override
    public String toString() {
        StringBuilder key = new StringBuilder();
        key.append(groupId).append(KEY_SEPARATOR).append(artifactId).append(KEY_SEPARATOR).append(type);

        if(classifier != null) {
            key.append(KEY_SEPARATOR).append(classifier);
        }

        return key.toString();
    }
}
