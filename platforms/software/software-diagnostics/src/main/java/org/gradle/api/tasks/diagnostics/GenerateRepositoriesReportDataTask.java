/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.tasks.diagnostics;

import com.google.common.collect.ImmutableList;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.artifacts.repositories.AbstractArtifactRepository;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.repositories.json.RepositoryReportDataJsonWriter;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.ReportRepository;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryDeclarationSite;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryReportModelFactory;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryReportProjectModel;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryRole;
import org.gradle.internal.serialization.Cached;
import org.gradle.work.DisableCachingByDefault;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryDeclarationSite.Scope.PROJECT;

/**
 * Produces a JSON file describing the repositories declared in this project's build script
 * (both `project.buildscript.repositories` and `project.repositories`).
 *
 * <p>This task is registered on every project by `SoftwareReportingTasksPlugin` and is
 * the producer half of the `repositories` report's variant-aware data exchange. The task
 * is invoked transitively when `:repositories` runs on the root project; it has no
 * `dependsOn` relationship to any lifecycle task.
 *
 * @since 9.7.0
 */
@Incubating
@DisableCachingByDefault(because = "Inputs are the project model, not file properties — task should run whenever invoked")
public abstract class GenerateRepositoriesReportDataTask extends DefaultTask {

    private final Cached<RepositoryReportProjectModel> capturedModel = Cached.of(this::capture);

    /**
     * The file to which the JSON-serialized repository data is written.
     *
     * @return property holding the output file
     * @since 9.7.0
     */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    void generate() {
        RepositoryReportDataJsonWriter.write(capturedModel.get(), getOutputFile().get().getAsFile());
    }

    private RepositoryReportProjectModel capture() {
        ProjectInternal pi = (ProjectInternal) getProject();
        String projectDisplayName = pi.getDisplayName();
        RepositoryReportModelFactory factory = new RepositoryReportModelFactory();
        RepositoryDeclarationSite buildscriptSite = new RepositoryDeclarationSite(
            PROJECT, pi.getProjectPath(), "buildscript.repositories");
        RepositoryDeclarationSite repositoriesSite = new RepositoryDeclarationSite(
            PROJECT, pi.getProjectPath(), "repositories");

        List<ReportRepository> buildscriptRepos = convertRepos(
            pi.getBuildscript().getRepositories(),
            factory,
            buildscriptSite,
            Set.of(RepositoryRole.PROJECT_BUILDSCRIPT_DEPENDENCIES),
            projectDisplayName
        );
        List<ReportRepository> projectRepos = convertRepos(
            pi.getRepositories(),
            factory,
            repositoriesSite,
            Set.of(RepositoryRole.PROJECT_DEPENDENCIES),
            projectDisplayName
        );
        return new RepositoryReportProjectModel(
            pi.getProjectPath(),
            pi.getName(),
            buildscriptRepos,
            projectRepos
        );
    }

    private List<ReportRepository> convertRepos(
        ArtifactRepositoryContainer container,
        RepositoryReportModelFactory factory,
        RepositoryDeclarationSite site,
        Set<RepositoryRole> roles,
        String projectDisplayName
    ) {
        List<ReportRepository> out = new ArrayList<>(container.size());
        for (ArtifactRepository repo : container) {
            if (!(repo instanceof AbstractArtifactRepository)) {
                getLogger().warn(
                    "Skipping repository '{}' in project {} > {}: not an AbstractArtifactRepository (class={})",
                    repo.getName(), projectDisplayName, site.getBlock(), repo.getClass().getName());
                continue;
            }
            out.add(factory.buildReportRepository((AbstractArtifactRepository) repo, roles, site));
        }
        return ImmutableList.copyOf(out);
    }
}
