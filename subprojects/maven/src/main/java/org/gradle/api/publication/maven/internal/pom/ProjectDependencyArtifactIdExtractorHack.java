/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publication.maven.internal.pom;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.plugins.BasePluginConvention;

import javax.annotation.Nullable;

/**
 * Given a project dependency, determines the artifact ID that the depended-on project
 * can be referred to from a Maven POM. Falls back to project.name if the artifact ID
 * used for publishing the depended-on project cannot be determined with certainty.
 *
 * The main goal of this class is to fix GRADLE-443 without changing any other existing
 * behavior (e.g. when a project that gets published to a Maven repo depends on a
 * project published to an Ivy repo).
 *
 * This class should be removed as soon as we have proper support for publications.
 */
class ProjectDependencyArtifactIdExtractorHack {
    private final Project project;

    public ProjectDependencyArtifactIdExtractorHack(ProjectDependency dependency) {
        this.project = dependency.getDependencyProject();
    }

    public String extract() {
        String baseName = getArchivesBaseName();
        return baseName != null ? baseName : project.getName();
    }

    @Nullable
    private String getArchivesBaseName() {
        BasePluginConvention convention = project.getConvention().findPlugin(BasePluginConvention.class);
        return convention != null ? convention.getArchivesBaseName() : null;
    }
}
