/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.SnapshottingFilter;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Provides access to snapshots of the content and metadata of the file system.
 *
 * The implementation will attempt to efficiently honour the queries, maintaining some or all state in-memory and dealing with concurrent access to the same parts of the file system.
 *
 * The virtual file system needs to be informed when some state on disk changes, so it does not become out of sync with the actual file system.
 */
public interface VirtualFileSystem {

    /**
     * Visits the hash of the content of the file only if the file is a regular file.
     *
     * @return the visitor function applied to the found snapshot.
     */
    <T> Optional<T> readRegularFileContentHash(String location, Function<HashCode, T> visitor);

    /**
     * Visits the hierarchy of files at the given location.
     */
    <T> T read(String location, Function<CompleteFileSystemLocationSnapshot, T> visitor);

    /**
     * Visits the hierarchy of files which match the filter at the given location.
     *
     * The consumer is only called if if something matches the filter.
     */
    void read(String location, SnapshottingFilter filter, Consumer<CompleteFileSystemLocationSnapshot> visitor);

    /**
     * Runs an action which potentially updates the given locations.
     */
    void update(Iterable<String> locations, Runnable action);

    /**
     * Removes all cached state from the virtual file system.
     */
    void invalidateAll();

    /**
     * Updates the cached state at the location with the snapshot.
     */
    void updateWithKnownSnapshot(CompleteFileSystemLocationSnapshot snapshot);

    /**
     * System property to enable feature preview for partial invalidation.
     */
    String VFS_PARTIAL_INVALIDATION_ENABLED_PROPERTY = "org.gradle.unsafe.vfs.partial-invalidation";

    static boolean isPartialInvalidationEnabled(Map<String, String> systemPropertiesArgs) {
        return getProperty(VFS_PARTIAL_INVALIDATION_ENABLED_PROPERTY, systemPropertiesArgs) != null;
    }

    /**
     * System property to enable feature preview for retaining VFS state between builds.
     */
    String VFS_RETENTION_ENABLED_PROPERTY = "org.gradle.unsafe.vfs.retention";

    static boolean isRetentionEnabled(Map<String, String> systemPropertiesArgs) {
        return getProperty(VFS_RETENTION_ENABLED_PROPERTY, systemPropertiesArgs) != null;
    }

    /**
     * When retention is enabled, this system property can be used to pass a comma-separated
     * list of file paths that have changed since the last build.
     *
     * @see #VFS_RETENTION_ENABLED_PROPERTY
     */
    String VFS_CHANGES_SINCE_LAST_BUILD_PROPERTY = "org.gradle.unsafe.vfs.changes";

    static Iterable<String> getChangedPathsSinceLastBuild(Map<String, String> systemPropertiesArgs) {
        String property = getProperty(VFS_CHANGES_SINCE_LAST_BUILD_PROPERTY, systemPropertiesArgs);
        if (property == null) {
            return ImmutableSet.of();
        }
        return Splitter.on(',')
            .omitEmptyStrings()
            .split(property);
    }

    @Nullable
    // private
    static String getProperty(String property, Map<String, String> systemPropertiesArgs) {
        return System.getProperty(property, systemPropertiesArgs.get(property));
    }
}
