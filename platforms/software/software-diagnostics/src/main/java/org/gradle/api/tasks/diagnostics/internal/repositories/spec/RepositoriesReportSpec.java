/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package org.gradle.api.tasks.diagnostics.internal.repositories.spec;

import org.gradle.api.tasks.diagnostics.internal.repositories.reachability.ReachabilityStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

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
        this.reachabilityByLocation = reachabilityByLocation;
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
