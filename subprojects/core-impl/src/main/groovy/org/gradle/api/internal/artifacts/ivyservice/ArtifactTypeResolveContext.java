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
package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.resolution.SoftwareArtifact;

public class ArtifactTypeResolveContext implements ArtifactResolveContext {
    private final Class<? extends SoftwareArtifact> artifactType;

    public ArtifactTypeResolveContext(Class<? extends SoftwareArtifact> artifactType) {
        this.artifactType = artifactType;
    }

    public Class<? extends SoftwareArtifact> getArtifactType() {
        return artifactType;
    }

    public String getId() {
        return "artifacts:" + artifactType.getName();
    }

    public String getDescription() {
        return String.format("artifacts of type %s", artifactType);
    }
}
