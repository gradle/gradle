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
package org.gradle.api.internal.artifacts.repositories.layout;

import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.descriptor.IvyRepositoryDescriptor;
import org.jspecify.annotations.Nullable;

import java.net.URI;

/**
 * A Repository Layout that applies the following patterns:
 * <ul>
 *     <li>Artifacts: $baseUri/{@value IvyArtifactRepository#MAVEN_ARTIFACT_PATTERN}</li>
 *     <li>Ivy: $baseUri/{@value IvyArtifactRepository#MAVEN_IVY_PATTERN}</li>
 * </ul>
 *
 * Following the Maven convention, the 'organisation' value is further processed by replacing '.' with '/'.
 * Note that the resolver will follow the layout only, but will <em>not</em> use .pom files for meta data. Ivy metadata files are required/published.
 */
public class MavenRepositoryLayout extends AbstractRepositoryLayout {
    @Override
    public void apply(@Nullable URI baseUri, IvyRepositoryDescriptor.Builder builder) {
        builder.setLayoutType("Maven");
        builder.setM2Compatible(true); // Replace '.' with '/' in organisation
        builder.addIvyPattern(IvyArtifactRepository.MAVEN_IVY_PATTERN);
        builder.addIvyResource(baseUri, IvyArtifactRepository.MAVEN_IVY_PATTERN);
        builder.addArtifactPattern(IvyArtifactRepository.MAVEN_ARTIFACT_PATTERN);
        builder.addArtifactResource(baseUri, IvyArtifactRepository.MAVEN_ARTIFACT_PATTERN);
    }
}
