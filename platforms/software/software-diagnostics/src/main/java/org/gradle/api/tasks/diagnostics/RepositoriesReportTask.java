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
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.repositories.AbstractArtifactRepository;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.ReportRepository;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryDeclarationSite;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryReportFullModel;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryReportModelFactory;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryReportProjectModel;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryReportSettingsModel;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryRole;
import org.gradle.api.tasks.diagnostics.internal.repositories.reachability.ReachabilityStatus;
import org.gradle.api.tasks.diagnostics.internal.repositories.reachability.RepositoryReachabilityChecker;
import org.gradle.api.tasks.diagnostics.internal.repositories.renderer.ConsoleRepositoriesReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.repositories.spec.RepositoriesReportSpec;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.serialization.Cached;
import org.gradle.util.Path;
import org.gradle.work.DisableCachingByDefault;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryDeclarationSite.Scope.PROJECT;
import static org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryDeclarationSite.Scope.SETTINGS;

/**
 * Displays a unified view of every repository used by the build, across settings-level
 * and project-level declaration sites, annotated with the kinds of dependencies each is allowed to serve.
 *
 * @since 9.6.0
 */
@Incubating
@DisableCachingByDefault(because = "Produces only non-cacheable console output")
public abstract class RepositoriesReportTask extends ConventionReportTask {
    private final Cached<RepositoryReportFullModel> fullModel = Cached.of(this::buildFullModel);

    @Inject
    @Override
    protected abstract StyledTextOutputFactory getTextOutputFactory();

    private final TextReportRenderer textRenderer = new TextReportRenderer();

    @Override
    protected TextReportRenderer getRenderer() {
        return textRenderer;
    }

    /**
     * Limits the report to a single project path.
     *
     * @return property holding the project path to filter to
     * @since 9.6.0
     */
    @Input
    @Optional
    @Option(option = "project", description = "Limits the report to a single project path")
    public abstract Property<String> getProjectFilter();

    @TaskAction
    void report() {
        RepositoryReportFullModel model = fullModel.get();
        boolean offline = getOfflineMode().get();
        Map<String, ReachabilityStatus> reachability;
        if (offline) {
            reachability = Collections.emptyMap();
        } else {
            List<ReportRepository> all = collectAllRepos(model);
            int probeCount = countProbeableLocations(all);
            if (probeCount > 0) {
                getLogger().lifecycle("Probing {} repository URL{}...", probeCount, probeCount == 1 ? "" : "s");
            }
            reachability = RepositoryReachabilityChecker.withDefaultTimeout().check(all, false);
            if (probeCount > 0) {
                getLogger().lifecycle("done.");
            }
        }
        RepositoriesReportSpec spec = new RepositoriesReportSpec(
            getProjectFilter().getOrNull(),
            offline,
            reachability
        );
        ConsoleRepositoriesReportRenderer renderer = new ConsoleRepositoriesReportRenderer(spec);
        StyledTextOutput out = getTextOutputFactory().create(getClass());
        renderer.render(model, out);
    }

    private static int countProbeableLocations(List<ReportRepository> all) {
        java.util.Set<String> uniqueProbeable = new java.util.LinkedHashSet<>();
        for (ReportRepository r : all) {
            if (r.getType() == org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryType.MAVEN_LOCAL
                || r.getType() == org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryType.FLAT_DIR) {
                continue;
            }
            String loc = r.getLocation();
            if (loc.startsWith("http://") || loc.startsWith("https://")) {
                uniqueProbeable.add(loc);
            }
        }
        return uniqueProbeable.size();
    }

    /**
     * Whether the build is running with {@code --offline}. Wired internally at registration time
     * from {@code gradle.startParameter.isOffline()} so that we do not touch {@link Project}
     * or the {@link org.gradle.api.invocation.Gradle Gradle} object at task-execution time
     * (which is CC-incompatible).
     *
     * @since 9.6.0
     */
    @Internal
    public abstract Property<Boolean> getOfflineMode();

    private static List<ReportRepository> collectAllRepos(RepositoryReportFullModel model) {
        List<ReportRepository> all = new ArrayList<>();
        all.addAll(model.getSettings().getSettingsBuildscriptRepositories());
        all.addAll(model.getSettings().getPluginManagementRepositories());
        all.addAll(model.getSettings().getDependencyResolutionManagementRepositories());
        for (RepositoryReportProjectModel p : model.getProjectsByPath().values()) {
            all.addAll(p.getBuildscriptRepositories());
            all.addAll(p.getProjectRepositories());
        }
        return all;
    }

    private RepositoryReportFullModel buildFullModel() {
        ProjectInternal root = (ProjectInternal) getProject().getRootProject();
        SettingsInternal settings = root.getGradle().getSettings();
        RepositoryReportModelFactory factory = new RepositoryReportModelFactory();

        // Walk order matches Gradle's actual repo-search sequence during a build: the
        // settings-script classpath is resolved first, then plugin resolution, then DRM/project deps.
        RepositoryReportSettingsModel settingsModel = new RepositoryReportSettingsModel(
            convertSettingsRepos(settings.getBuildscript().getRepositories(), factory,
                new RepositoryDeclarationSite(SETTINGS, null, "buildscript.repositories"),
                Set.of(RepositoryRole.SETTINGS_BUILDSCRIPT_DEPENDENCIES)),
            convertSettingsRepos(settings.getPluginManagement().getRepositories(), factory,
                new RepositoryDeclarationSite(SETTINGS, null, "pluginManagement.repositories"),
                Set.of(RepositoryRole.PLUGINS)),
            convertSettingsRepos(settings.getDependencyResolutionManagement().getRepositories(), factory,
                new RepositoryDeclarationSite(SETTINGS, null, "dependencyResolutionManagement.repositories"),
                Set.of(RepositoryRole.PROJECT_DEPENDENCIES))
        );

        TreeMap<Path, RepositoryReportProjectModel> projects = new TreeMap<>(RepositoryReportFullModel.getPathComparator());
        for (Project p : root.getAllprojects()) {
            ProjectInternal pi = (ProjectInternal) p;
            projects.put(pi.getProjectPath(),
                buildProjectModel(pi, factory));
        }

        return new RepositoryReportFullModel(settingsModel, projects);
    }

    private RepositoryReportProjectModel buildProjectModel(ProjectInternal project, RepositoryReportModelFactory factory) {
        RepositoryDeclarationSite buildscriptSite = new RepositoryDeclarationSite(PROJECT, project.getProjectPath(), "buildscript.repositories");
        RepositoryDeclarationSite repositoriesSite = new RepositoryDeclarationSite(PROJECT, project.getProjectPath(), "repositories");

        List<ReportRepository> buildscriptRepos = convertRepos(
            project.getBuildscript().getRepositories(), factory, buildscriptSite,
            Set.of(RepositoryRole.PROJECT_BUILDSCRIPT_DEPENDENCIES),
            project
        );
        List<ReportRepository> projectRepos = convertRepos(
            project.getRepositories(), factory, repositoriesSite,
            Set.of(RepositoryRole.PROJECT_DEPENDENCIES),
            project
        );
        return new RepositoryReportProjectModel(
            project.getProjectPath(),
            project.getName(),
            buildscriptRepos,
            projectRepos
        );
    }

    private List<ReportRepository> convertSettingsRepos(
        ArtifactRepositoryContainer container,
        RepositoryReportModelFactory factory,
        RepositoryDeclarationSite site,
        Set<RepositoryRole> roles
    ) {
        return convertRepos(container, factory, site, roles, null);
    }

    private List<ReportRepository> convertRepos(
        ArtifactRepositoryContainer container,
        RepositoryReportModelFactory factory,
        RepositoryDeclarationSite site,
        Set<RepositoryRole> roles,
        @Nullable Project projectForLogging
    ) {
        List<ReportRepository> out = new ArrayList<>(container.size());
        for (ArtifactRepository repo : container) {
            if (!(repo instanceof AbstractArtifactRepository)) {
                String context = projectForLogging != null ? "project " + projectForLogging : "settings";
                getLogger().warn("Skipping repository '{}' in {} > {}: not an AbstractArtifactRepository (class={})",
                    repo.getName(), context, site.getBlock(), repo.getClass().getName());
                continue;
            }
            out.add(factory.buildReportRepository((AbstractArtifactRepository) repo, roles, site));
        }
        return ImmutableList.copyOf(out);
    }
}
