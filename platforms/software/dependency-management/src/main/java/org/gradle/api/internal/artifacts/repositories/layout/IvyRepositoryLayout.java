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
 *     <li>Artifacts: $baseUri/{@value IvyArtifactRepository#IVY_ARTIFACT_PATTERN}</li>
 *     <li>Ivy: $baseUri/{@value IvyArtifactRepository#IVY_ARTIFACT_PATTERN}</li>
 * </ul>
 */
public class IvyRepositoryLayout extends AbstractRepositoryLayout {
    @Override
    public void apply(@Nullable URI baseUri, IvyRepositoryDescriptor.Builder builder) {
        builder.setLayoutType("Ivy");
        builder.setM2Compatible(false);
        builder.addIvyPattern(IvyArtifactRepository.IVY_ARTIFACT_PATTERN);
        builder.addIvyResource(baseUri, IvyArtifactRepository.IVY_ARTIFACT_PATTERN);
        builder.addArtifactPattern(IvyArtifactRepository.IVY_ARTIFACT_PATTERN);
        builder.addArtifactResource(baseUri, IvyArtifactRepository.IVY_ARTIFACT_PATTERN);
    }
}
