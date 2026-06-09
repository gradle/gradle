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
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.repositories.AbstractArtifactRepository;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.repositories.json.RepositoryReportDataJsonReader;
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

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryDeclarationSite.Scope.SETTINGS;

/**
 * Displays a unified view of every repository used by the build, across settings-level
 * and project-level declaration sites, annotated with the kinds of dependencies each is allowed to serve.
 *
 * <p><strong>Task placement invariants</strong>:
 * <ul>
 *   <li>This task is registered ONLY on the root project. {@code :sub:repositories} does not exist.</li>
 *   <li>Per-project repository data is gathered via variant-aware dependency resolution: each
 *       project's {@code generateRepositoriesReportData} task produces a JSON file consumed via
 *       this task's {@code repositoriesData} resolvable Configuration. No cross-project state
 *       access at configuration time — Isolated Projects compatible.</li>
 *   <li>The resolvable Configuration always depends on every project, regardless of the
 *       {@code --project} filter value. Filtering happens render-only on the aggregated model.
 *       This keeps the Configuration's identity stable and allows configuration cache reuse
 *       across filter changes.</li>
 * </ul>
 *
 * @since 9.7.0
 */
@Incubating
@DisableCachingByDefault(because = "Produces only non-cacheable console output")
public abstract class RepositoriesReportTask extends ConventionReportTask {
    private final Cached<RepositoryReportSettingsModel> settingsModel = Cached.of(this::buildSettingsModel);
    private final Cached<Boolean> offline = Cached.of(this::detectOffline);

    @Inject
    @Override
    protected abstract StyledTextOutputFactory getTextOutputFactory();

    private final TextReportRenderer textRenderer = new TextReportRenderer();

    @Override
    protected TextReportRenderer getRenderer() {
        return textRenderer;
    }

    /**
     * Resolved per-project repository data files, supplied by the {@code repositoriesData}
     * resolvable Configuration. Wired by {@code SoftwareReportingTasksPlugin}.
     *
     * <p>Marked {@code @Internal} on purpose: declaring this as {@code @InputFiles} would
     * force Gradle to fingerprint the configuration's resolved files at task-execution time,
     * which fires {@code ResolveConfigurationResolutionBuildOperationDetails} and eagerly
     * materializes every repository descriptor for the resolving project. That descriptor
     * pass calls {@code DefaultMavenArtifactRepository.createDescriptor()} which calls
     * {@code populateAuthenticationCredentials()} — and that triggers a {@code .getOrNull()}
     * on any {@code credentials(SomeCredentialsType)} provider, failing with
     * {@code MissingValueException} when the corresponding {@code mavenUsername} /
     * {@code mavenPassword} Gradle properties are absent. Treating the data as an internal
     * collection (with execution ordering established by {@code dependsOn(configuration)})
     * keeps the producer→consumer wiring without forcing eager credential evaluation.
     *
     * @return file collection of JSON data files, one per project
     * @since 9.7.0
     */
    @Internal
    public abstract ConfigurableFileCollection getRepositoriesData();

    /**
     * Limits the report to a single project path.
     *
     * @return property holding the project path to filter to
     * @since 9.7.0
     */
    @Input
    @Optional
    @Option(option = "project", description = "Limits the report to a single project path")
    public abstract Property<String> getProjectFilter();

    @TaskAction
    void report() {
        RepositoryReportFullModel model = assembleFullModel();
        Map<String, ReachabilityStatus> reachability;
        if (model.isOffline()) {
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
            reachability
        );
        ConsoleRepositoriesReportRenderer renderer = new ConsoleRepositoriesReportRenderer(spec);
        StyledTextOutput out = getTextOutputFactory().create(getClass());
        renderer.render(model, out);
    }

    private RepositoryReportFullModel assembleFullModel() {
        TreeMap<Path, RepositoryReportProjectModel> projects = new TreeMap<>(RepositoryReportFullModel.getPathComparator());
        for (File f : getRepositoriesData()) {
            RepositoryReportProjectModel projectModel = RepositoryReportDataJsonReader.read(f);
            projects.put(projectModel.getProjectPath(), projectModel);
        }
        return new RepositoryReportFullModel(offline.get(), settingsModel.get(), projects);
    }

    private RepositoryReportSettingsModel buildSettingsModel() {
        ProjectInternal root = (ProjectInternal) getProject().getRootProject();
        SettingsInternal settings = root.getGradle().getSettings();
        RepositoryReportModelFactory factory = new RepositoryReportModelFactory();

        return new RepositoryReportSettingsModel(
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
    }

    private boolean detectOffline() {
        ProjectInternal pi = (ProjectInternal) getProject();
        return pi.getGradle().getStartParameter().isOffline();
    }

    private static int countProbeableLocations(List<ReportRepository> all) {
        Set<String> uniqueProbeable = new LinkedHashSet<>();
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

    private List<ReportRepository> convertSettingsRepos(
        ArtifactRepositoryContainer container,
        RepositoryReportModelFactory factory,
        RepositoryDeclarationSite site,
        Set<RepositoryRole> roles
    ) {
        List<ReportRepository> out = new ArrayList<>(container.size());
        for (ArtifactRepository repo : container) {
            if (!(repo instanceof AbstractArtifactRepository)) {
                getLogger().warn("Skipping repository '{}' in settings > {}: not an AbstractArtifactRepository (class={})",
                    repo.getName(), site.getBlock(), repo.getClass().getName());
                continue;
            }
            out.add(factory.buildReportRepository((AbstractArtifactRepository) repo, roles, site));
        }
        return ImmutableList.copyOf(out);
    }
}
