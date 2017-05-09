/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifacts;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.VariantMetadata;

import java.util.Set;

public class ProjectDependencyComponentArtifacts implements ComponentArtifacts {
    private final ProjectArtifactBuilder builder;
    private final ComponentArtifacts delegate;

    public ProjectDependencyComponentArtifacts(ProjectArtifactBuilder builder, ComponentArtifacts delegate) {
        this.builder = builder;
        this.delegate = delegate;
    }

    @Override
    public Set<? extends VariantMetadata> getVariantsFor(ConfigurationMetadata configuration) {
        Set<? extends VariantMetadata> variants = delegate.getVariantsFor(configuration);
        for (VariantMetadata variant : variants) {
            for (ComponentArtifactMetadata artifactMetadata : variant.getArtifacts()) {
                builder.willBuild(artifactMetadata);
            }
        }
        return variants;
    }
}
