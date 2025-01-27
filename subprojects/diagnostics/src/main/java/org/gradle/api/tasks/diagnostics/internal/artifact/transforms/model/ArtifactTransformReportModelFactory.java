/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.artifact.transforms.model;

import org.gradle.api.Project;
import org.gradle.api.internal.artifacts.TransformRegistration;
import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.artifacts.transform.Transform;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Factory for creating {@link ArtifactTransformReportModel} instances which represent the Artifact Transforms present in a project.
 */
public abstract class ArtifactTransformReportModelFactory {
    private final VariantTransformRegistry variantTransformRegistry;

    @Inject
    public ArtifactTransformReportModelFactory(VariantTransformRegistry variantTransformRegistry) {
        this.variantTransformRegistry = variantTransformRegistry;
    }

    public ArtifactTransformReportModel buildForProject(Project project) {
        List<ReportArtifactTransform> artifactTransformData = variantTransformRegistry.getRegistrations().stream()
            .map(this::convertArtifactTransform)
            .collect(Collectors.toList());

        return new ArtifactTransformReportModel(project.getDisplayName(), artifactTransformData);
    }

    private ReportArtifactTransform convertArtifactTransform(TransformRegistration transformRegistration) {
        Transform transform = transformRegistration.getTransformStep().getTransform();
        return new ReportArtifactTransform(
            transform.getImplementationClass().getSimpleName(),
            transform.getImplementationClass(),
            transform.getFromAttributes(),
            transform.getToAttributes(),
            transform.isCacheable()
        );
    }
}
