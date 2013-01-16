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
package org.gradle.api.publish.maven.internal.artifact;

import org.apache.commons.lang.ObjectUtils;
import org.gradle.api.publish.maven.MavenArtifact;

public class MavenArtifactKey {
    private final String extension;
    private final String classifier;

    public MavenArtifactKey(MavenArtifact artifact) {
        this.extension = artifact.getExtension();
        this.classifier = artifact.getClassifier();
    }

    @Override
    public boolean equals(Object o) {
        MavenArtifactKey other = (MavenArtifactKey) o;
        return ObjectUtils.equals(extension, other.extension) && ObjectUtils.equals(classifier, other.classifier);
    }

    @Override
    public int hashCode() {
        return ObjectUtils.hashCode(extension) ^ ObjectUtils.hashCode(classifier);
    }
}
