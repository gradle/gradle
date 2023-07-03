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

package org.gradle.api.reporting.dependencies.internal;

import com.google.common.collect.Iterables;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.tasks.diagnostics.internal.ConfigurationDetails;
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails.ProjectNameAndPath;
import org.gradle.api.tasks.diagnostics.internal.ProjectsWithConfigurations;
import org.gradle.reporting.ReportRenderer;
import org.gradle.util.internal.GFileUtils;

import java.io.File;

public class JsonDependencyReporter extends ReportRenderer<ProjectsWithConfigurations<ProjectNameAndPath, ConfigurationDetails>, File> {

    private final JsonProjectDependencyRenderer renderer;

    public JsonDependencyReporter(VersionSelectorScheme versionSelectorScheme, VersionComparator versionComparator, VersionParser versionParser) {
        renderer = new JsonProjectDependencyRenderer(versionSelectorScheme, versionComparator, versionParser);
    }

    @Override
    public void render(final ProjectsWithConfigurations<ProjectNameAndPath, ConfigurationDetails> projectsWithConfigurations, File outputFile) {
        String json;
        if (projectsWithConfigurations.getProjects().size() > 1) {
            json = renderer.render(projectsWithConfigurations);
        } else {
            ProjectNameAndPath p = Iterables.getOnlyElement(projectsWithConfigurations.getProjects());
            json = renderer.render(p, projectsWithConfigurations.getConfigurationsFor(p));
        }
        GFileUtils.writeFile(json, outputFile, "utf-8");
    }
}
