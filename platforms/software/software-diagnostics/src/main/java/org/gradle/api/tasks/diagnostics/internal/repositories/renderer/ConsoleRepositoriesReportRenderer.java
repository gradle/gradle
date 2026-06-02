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

package org.gradle.api.tasks.diagnostics.internal.repositories.renderer;

import org.gradle.api.tasks.diagnostics.internal.repositories.model.ReportContentFilter;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.ReportRepository;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryReportFullModel;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryReportProjectModel;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryRole;
import org.gradle.api.tasks.diagnostics.internal.repositories.reachability.ReachabilityStatus;
import org.gradle.api.tasks.diagnostics.internal.repositories.spec.RepositoriesReportSpec;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Failure;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Header;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Identifier;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;

/**
 * Text renderer for the repositories report. Produces the "All Repositories" and
 * "Repositories by Location" sections plus an optional Legend, honoring the filter
 * and offline state carried in the supplied {@link RepositoriesReportSpec}.
 */
@NullMarked
public final class ConsoleRepositoriesReportRenderer {
    private static final String DIVIDER = "--------------------------------------------------------";
    private static final String LEGEND_URL = "https://docs.gradle.org/current/userguide/centralizing_repositories.html";

    private final RepositoriesReportSpec spec;

    public ConsoleRepositoriesReportRenderer(RepositoriesReportSpec spec) {
        this.spec = spec;
    }

    public void render(RepositoryReportFullModel model, StyledTextOutput out) {
        if (spec.isFiltered()) {
            validateFilter(model);
        }
        if (isModelEmpty(model)) {
            if (spec.isFiltered()) {
                out.println("There are no repositories present in project '" + spec.getProjectFilter() + "'.");
            } else {
                out.println("There are no repositories present.");
            }
            return;
        }
        if (spec.isFiltered()) {
            renderFiltered(model, out);
        } else {
            renderFull(model, out);
        }
    }

    private void validateFilter(RepositoryReportFullModel model) {
        String filter = spec.getProjectFilter();
        Path filterPath = Path.path(filter);
        if (!model.getProjectsByPath().containsKey(filterPath)) {
            throw new IllegalArgumentException(
                "Project '" + filter + "' not found. Available projects: "
                    + model.getProjectsByPath().keySet());
        }
    }

    private boolean isModelEmpty(RepositoryReportFullModel model) {
        var settings = model.getSettings();
        if (!settings.getPluginManagementRepositories().isEmpty()) {
            return false;
        }
        if (!settings.getSettingsBuildscriptRepositories().isEmpty()) {
            return false;
        }
        if (!settings.getDependencyResolutionManagementRepositories().isEmpty()) {
            return false;
        }
        for (RepositoryReportProjectModel p : model.getProjectsByPath().values()) {
            if (!p.getBuildscriptRepositories().isEmpty() || !p.getProjectRepositories().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void renderFull(RepositoryReportFullModel model, StyledTextOutput out) {
        var settings = model.getSettings();
        var projectsByPath = model.getProjectsByPath();
        boolean offline = model.isOffline();
        List<ReportRepository> pluginMgmt = settings.getPluginManagementRepositories();
        List<ReportRepository> settingsBuildscript = settings.getSettingsBuildscriptRepositories();
        List<ReportRepository> drm = settings.getDependencyResolutionManagementRepositories();

        // Section 1: All Repositories — flat, globally numbered in the following order:
        //   SETTINGS_BUILDSCRIPT_DEPENDENCIES, PLUGINS, settings DRM,
        //   then (alphabetical by project) buildscript repos + project.repositories.
        List<ReportRepository> allRepos = new ArrayList<>();
        allRepos.addAll(settingsBuildscript);
        allRepos.addAll(pluginMgmt);
        allRepos.addAll(drm);
        for (RepositoryReportProjectModel p : projectsByPath.values()) {
            allRepos.addAll(p.getBuildscriptRepositories());
            allRepos.addAll(p.getProjectRepositories());
        }

        Map<ReportRepository, Integer> numbers = assignNumbers(allRepos);
        Set<ReportRepository> starred = computeStarred(allRepos);
        MarkerSet markers = new MarkerSet();

        renderSection("All Repositories", allRepos, numbers, starred, out, true, markers, offline);

        // Section 2: Repositories by Location — settings block (all three buckets, in section-1 order) first,
        // then per-project blocks in alphabetical order.
        List<ReportRepository> settingsRefs = new ArrayList<>();
        settingsRefs.addAll(settingsBuildscript);
        settingsRefs.addAll(pluginMgmt);
        settingsRefs.addAll(drm);

        Map<Path, List<ReportRepository>> projectRefs = new LinkedHashMap<>();
        for (Map.Entry<Path, RepositoryReportProjectModel> e : projectsByPath.entrySet()) {
            List<ReportRepository> refs = new ArrayList<>();
            // Settings-inherited buckets are listed in Gradle's actual repo-search order
            // (pluginManagement precedes DRM — settings.buildscript is not inherited per-project).
            refs.addAll(pluginMgmt);
            refs.addAll(drm);
            refs.addAll(e.getValue().getBuildscriptRepositories());
            refs.addAll(e.getValue().getProjectRepositories());
            projectRefs.put(e.getKey(), refs);
        }

        renderByLocationSection(settingsRefs, projectRefs, numbers, out);

        if (!starred.isEmpty()) {
            markers.starUsed = true;
        }
        renderLegendIfNeeded(markers, out);
    }

    private void renderFiltered(RepositoryReportFullModel model, StyledTextOutput out) {
        String filter = spec.getProjectFilter();
        Path filterPath = Path.path(filter);
        RepositoryReportProjectModel target = model.getProjectsByPath().get(filterPath);
        assert target != null : "validateFilter() should have rejected unknown project '" + filter + "'";

        var settings = model.getSettings();
        boolean offline = model.isOffline();
        List<ReportRepository> filtered = new ArrayList<>();
        filtered.addAll(settings.getPluginManagementRepositories());
        filtered.addAll(settings.getDependencyResolutionManagementRepositories());
        filtered.addAll(target.getBuildscriptRepositories());
        filtered.addAll(target.getProjectRepositories());

        Map<ReportRepository, Integer> numbers = assignNumbers(filtered);
        Set<ReportRepository> starred = computeStarred(filtered);
        MarkerSet markers = new MarkerSet();

        renderSection("All Repositories", filtered, numbers, starred, out, true, markers, offline);

        // Section 2 in filtered mode: ONLY the target project's block, no settings block.
        Map<Path, List<ReportRepository>> projectRefs = new LinkedHashMap<>();
        projectRefs.put(filterPath, filtered);
        renderByLocationSection(null, projectRefs, numbers, out);

        if (!starred.isEmpty()) {
            markers.starUsed = true;
        }
        renderLegendIfNeeded(markers, out);
    }

    private void renderByLocationSection(
        @Nullable List<ReportRepository> settingsRefs,
        Map<Path, List<ReportRepository>> projectRefs,
        Map<ReportRepository, Integer> numbers,
        StyledTextOutput out
    ) {
        boolean hasSettingsBlock = settingsRefs != null && !settingsRefs.isEmpty();
        Map<Path, List<ReportRepository>> nonEmptyProjectRefs = new LinkedHashMap<>();
        for (Map.Entry<Path, List<ReportRepository>> e : projectRefs.entrySet()) {
            if (!e.getValue().isEmpty()) {
                nonEmptyProjectRefs.put(e.getKey(), e.getValue());
            }
        }
        if (!hasSettingsBlock && nonEmptyProjectRefs.isEmpty()) {
            return;
        }

        out.println();
        out.println(DIVIDER);
        out.withStyle(Header).println("Repositories by Location");
        out.println(DIVIDER);
        out.println();

        boolean first = true;
        if (hasSettingsBlock) {
            renderLocationBlock("settings uses", settingsRefs, numbers, out);
            first = false;
        }
        for (Map.Entry<Path, List<ReportRepository>> e : nonEmptyProjectRefs.entrySet()) {
            if (!first) {
                out.println();
            }
            first = false;
            renderLocationBlock("project '" + e.getKey() + "' uses", e.getValue(), numbers, out);
        }
    }

    private void renderLocationBlock(
        String header,
        List<ReportRepository> refs,
        Map<ReportRepository, Integer> numbers,
        StyledTextOutput out
    ) {
        out.println(header);
        for (ReportRepository r : refs) {
            out.println("    - " + r.getName() + " (" + numbers.get(r) + ")");
        }
    }

    private static Map<ReportRepository, Integer> assignNumbers(List<ReportRepository> repos) {
        IdentityHashMap<ReportRepository, Integer> numbers = new IdentityHashMap<>();
        int n = 1;
        for (ReportRepository r : repos) {
            numbers.put(r, n++);
        }
        return numbers;
    }

    private Set<ReportRepository> computeStarred(List<ReportRepository> all) {
        Map<ReportRepository.IdentityKey, Integer> counts = new HashMap<>();
        for (ReportRepository r : all) {
            counts.merge(r.getIdentityKey(), 1, Integer::sum);
        }
        Set<ReportRepository> starred = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ReportRepository r : all) {
            if (counts.get(r.getIdentityKey()) > 1) {
                starred.add(r);
            }
        }
        return starred;
    }

    private void renderSection(
        String heading,
        List<ReportRepository> repos,
        Map<ReportRepository, Integer> numbers,
        Set<ReportRepository> starred,
        StyledTextOutput out,
        boolean allowEmpty,
        MarkerSet markers,
        boolean offline
    ) {
        out.println();
        out.println(DIVIDER);
        String renderedHeading = heading;
        if ("All Repositories".equals(heading) && offline) {
            renderedHeading = heading + " (o)";
            markers.offlineUsed = true;
        }
        out.withStyle(Header).println(renderedHeading);
        out.println(DIVIDER);
        out.println();
        if (repos.isEmpty()) {
            if (allowEmpty) {
                out.withStyle(Info).println("(none)");
            }
            return;
        }
        boolean first = true;
        for (ReportRepository r : repos) {
            if (!first) {
                out.println();
            }
            first = false;
            renderRepoEntry(r, numbers.get(r), starred.contains(r), out, markers, offline);
        }
    }

    private void renderRepoEntry(ReportRepository r, int number, boolean star, StyledTextOutput out, MarkerSet markers, boolean offline) {
        StringBuilder header = new StringBuilder(r.getName()).append(" (").append(number).append(")");
        if (star) {
            header.append(" *");
        }
        // Reachability markers are suppressed in offline mode; the (o) on the All Repositories
        // heading already communicates that no probes were performed.
        String locationMarker = "";
        if (!offline) {
            ReachabilityStatus status = spec.getReachabilityByLocation().get(r.getLocation());
            if (status == ReachabilityStatus.UNREACHABLE) {
                locationMarker = " (ur)";
                markers.unreachableUsed = true;
            } else if (status == ReachabilityStatus.UNAUTHORIZED) {
                locationMarker = " (ua)";
                markers.unauthorizedUsed = true;
            } else if (status == ReachabilityStatus.MALFORMED_URL) {
                locationMarker = " (m)";
                markers.malformedUsed = true;
            }
        }
        out.withStyle(Identifier).text(header.toString());
        out.println();
        out.println("    Location:   " + r.getLocation() + locationMarker);
        out.println("    Type:       " + r.getType());
        out.println("    Roles:      " + r.getRoles().stream()
            .sorted()
            .map(RepositoryRole::name)
            .collect(Collectors.joining(", ")));
        if (!r.isSecure()) {
            out.withStyle(Failure).println("    Secure:     false");
        }
        if (!r.getAuthSchemes().isEmpty()) {
            out.println("    Auth:       " + String.join(", ", r.getAuthSchemes()));
        }
        if (r.hasCredentials()) {
            out.println("    Credentials: PRESENT");
        }
        if (!r.getContentFilter().isEmpty()) {
            out.println("    Content:    " + renderContent(r.getContentFilter()));
        }
        out.println("    Defined in:  " + r.getDeclarationSite().render());
    }

    private String renderContent(ReportContentFilter f) {
        List<String> parts = new ArrayList<>();
        parts.addAll(f.getIncludeRules());
        parts.addAll(f.getExcludeRules());
        if (!f.getOnlyForConfigurations().isEmpty()) {
            parts.add("onlyForConfigurations(" + f.getOnlyForConfigurations() + ")");
        }
        if (!f.getNotForConfigurations().isEmpty()) {
            parts.add("notForConfigurations(" + f.getNotForConfigurations() + ")");
        }
        for (Map.Entry<String, Set<String>> a : f.getOnlyForAttributes().entrySet()) {
            parts.add("onlyForAttribute(" + a.getKey() + ", " + a.getValue() + ")");
        }
        return String.join(", ", parts);
    }

    private void renderLegendIfNeeded(MarkerSet markers, StyledTextOutput out) {
        if (!markers.any()) {
            return;
        }
        out.println();
        out.println(DIVIDER);
        out.withStyle(Header).println("Legend");
        out.println(DIVIDER);
        out.println();
        StyledTextOutput info = out.withStyle(Info);
        if (markers.starUsed) {
            info.println("(*) Identical repository declaration found in multiple locations.");
            info.println("    Consider consolidating to settings.dependencyResolutionManagement.repositories");
            info.println("    or settings.pluginManagement.repositories.");
            info.println("    See " + LEGEND_URL);
        }
        if (markers.unreachableUsed) {
            info.println("(ur) Unreachable \u2014 the URL could not be contacted.");
        }
        if (markers.unauthorizedUsed) {
            info.println("(ua) Unauthorized \u2014 the URL returned 401/403; credentials were not sent.");
        }
        if (markers.malformedUsed) {
            info.println("(m)  Malformed URL \u2014 the URL could not be parsed.");
        }
        if (markers.offlineUsed) {
            info.println("(o)  Running in offline mode \u2014 no reachability checks were performed.");
        }
    }

    /**
     * Tracks which legend-worthy markers were actually emitted by the renderer so the Legend
     * section only surfaces entries that correspond to visible markers.
     */
    private static final class MarkerSet {
        boolean starUsed;
        boolean unreachableUsed;
        boolean unauthorizedUsed;
        boolean malformedUsed;
        boolean offlineUsed;

        boolean any() {
            return starUsed || unreachableUsed || unauthorizedUsed || malformedUsed || offlineUsed;
        }
    }
}
