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

package org.gradle.api.tasks.diagnostics.internal.repositories.model;

import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;

import java.io.Serializable;
import java.util.Comparator;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Aggregates the settings-level model and the per-project models into a single
 * configuration-cache-serializable payload consumed by
 * {@link org.gradle.api.tasks.diagnostics.internal.repositories.renderer.ConsoleRepositoriesReportRenderer}.
 */
@NullMarked
public final class RepositoryReportFullModel {
    private static final PathComparator PATH_COMPARATOR = new PathComparator();

    private final boolean offline;
    private final RepositoryReportSettingsModel settings;
    private final SortedMap<Path, RepositoryReportProjectModel> projectsByPath;

    public static Comparator<Path> getPathComparator() {
        return PATH_COMPARATOR;
    }

    public RepositoryReportFullModel(
        boolean offline,
        RepositoryReportSettingsModel settings,
        SortedMap<Path, RepositoryReportProjectModel> projectsByPath
    ) {
        this.offline = offline;
        this.settings = settings;
        // Use a plain TreeMap with a named Serializable Comparator rather than Guava's
        // ImmutableSortedMap so the configuration-cache serializer can round-trip this field.
        // TreeMap has a dedicated CC codec; a lambda-based Comparator would not survive CC
        // because the synthetic lambda class is not resolvable from the Gradle runtime class loader
        // during cache reuse.
        TreeMap<Path, RepositoryReportProjectModel> copy = new TreeMap<>(PATH_COMPARATOR);
        copy.putAll(projectsByPath);
        this.projectsByPath = copy;
    }

    /**
     * Whether the build was invoked with {@code --offline}. Captured into the model so the
     * single {@code Cached<RepositoryReportFullModel>} snapshot already contains everything the
     * renderer needs, and the task action does not need to access
     * {@link org.gradle.api.invocation.Gradle Gradle} or {@code StartParameter} at execution time
     * (which would be CC-incompatible). Configuration cache re-validates this flag via
     * {@code StartParameter} being a tracked CC input.
     */
    public boolean isOffline() {
        return offline;
    }

    public RepositoryReportSettingsModel getSettings() {
        return settings;
    }

    public SortedMap<Path, RepositoryReportProjectModel> getProjectsByPath() {
        return projectsByPath;
    }

    private static final class PathComparator implements Comparator<Path>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public int compare(Path a, Path b) {
            return a.asString().compareTo(b.asString());
        }
    }
}
