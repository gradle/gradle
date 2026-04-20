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

package org.gradle.api.tasks.diagnostics.internal.repositories.spec;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.tasks.diagnostics.internal.repositories.reachability.ReachabilityStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * Read-only parameter object passed to {@code ConsoleRepositoriesReportRenderer} describing
 * how the report should be rendered: the optional project filter, whether the build is in
 * offline mode, and the per-location reachability results (empty when offline).
 */
@NullMarked
public final class RepositoriesReportSpec {
    private final @Nullable String projectFilter;
    private final boolean offline;
    private final Map<String, ReachabilityStatus> reachabilityByLocation;

    public RepositoriesReportSpec(@Nullable String projectFilter) {
        this(projectFilter, false, Collections.emptyMap());
    }

    public RepositoriesReportSpec(
        @Nullable String projectFilter,
        boolean offline,
        Map<String, ReachabilityStatus> reachabilityByLocation
    ) {
        this.projectFilter = projectFilter;
        this.offline = offline;
        // Defensive copy — the spec is shared with the renderer and must not observe
        // mutations from the caller after construction.
        this.reachabilityByLocation = ImmutableMap.copyOf(reachabilityByLocation);
    }

    public @Nullable String getProjectFilter() {
        return projectFilter;
    }

    public boolean isFiltered() {
        return projectFilter != null;
    }

    /**
     * Whether the build was invoked with {@code --offline}. When true, no reachability probes
     * were performed and {@link #getReachabilityByLocation()} is empty; the renderer appends
     * an {@code (o)} marker to the "All Repositories" heading instead of per-repo markers.
     */
    public boolean isOffline() {
        return offline;
    }

    /**
     * Per-location reachability status for remote HTTP(S) repositories. Keys are the literal
     * {@code location} strings of {@code ReportRepository} entries. Repositories whose locations
     * are not in this map carry no reachability marker (e.g. local repos, or when offline).
     */
    public Map<String, ReachabilityStatus> getReachabilityByLocation() {
        return reachabilityByLocation;
    }
}
