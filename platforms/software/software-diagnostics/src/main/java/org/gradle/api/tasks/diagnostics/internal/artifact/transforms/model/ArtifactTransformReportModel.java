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

import java.util.List;
import java.util.stream.Collectors;

/**
 * Lightweight, immutable model of all the data in a project necessary for Artifact Transform reporting.
 *
 * The intended use is that this data model can be populated with the complete information of a project prior to any
 * report logic running.  This enables the reporting logic to remain completely independent of the actual project classes.
 */
public final class ArtifactTransformReportModel {
    private final String projectDisplayName;
    private final List<ReportArtifactTransform> transforms;

    ArtifactTransformReportModel(String projectDisplayName, List<ReportArtifactTransform> transforms) {
        this.projectDisplayName = projectDisplayName;
        this.transforms = transforms;
    }

    public String getProjectDisplayName() {
        return projectDisplayName;
    }

    public List<ReportArtifactTransform> getTransforms() {
        return transforms;
    }

    /**
     * Builds a new model containing all the transforms in this model of types containing the type name.
     *
     * @param typeName the type name to filter by
     * @return a new model containing only the transforms that match the type name
     */
    public ArtifactTransformReportModel filterTransformsByType(String typeName) {
        List<ReportArtifactTransform> matchingTransforms = transforms.stream()
            .filter(t -> t.getTransformClass().getSimpleName().contains(typeName))
            .collect(Collectors.toList());
        return new ArtifactTransformReportModel(projectDisplayName, matchingTransforms);
    }
}
