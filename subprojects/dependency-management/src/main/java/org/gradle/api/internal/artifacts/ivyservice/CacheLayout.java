/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.cache.internal.CacheVersion;
import org.gradle.cache.internal.CacheVersionMapping;

import javax.annotation.Nullable;
import java.io.File;

import static org.gradle.cache.internal.CacheVersionMapping.introducedIn;

/**
 * Versioned locations of global caches.
 *
 * The purpose of tracking previous versions is to help with cache cleanup.
 * E.g. when the layout was changed in {@code version1}, and it gets changed
 * in {@code version2} once again, we can delete the {@code version1} cache
 * when we detect that it is no longer used.
 *
 * Always use release candidate versions since we normally
 * don’t do code changes (and thus no cache layout changes) in final versions.
 */
public enum CacheLayout {

    ROOT(null, "modules", introducedIn("1.9-rc-1").incrementedIn("1.9-rc-2")),

    // If you update FILE_STORE, you may also need to update LocallyAvailableResourceFinderFactory
    FILE_STORE(ROOT, "files", introducedIn("1.9-rc-1")),

    META_DATA(ROOT, "metadata",
        // skipped versions were not used in a release
        introducedIn("1.9-rc-2")
        .changedTo(2, "1.11-rc-1")
        .changedTo(6, "1.12-rc-1")
        .changedTo(12, "2.0-rc-1")
        .changedTo(13, "2.1-rc-3")
        .changedTo(14, "2.2-rc-1")
        .changedTo(15, "2.4-rc-1")
        .changedTo(16, "2.8-rc-1")
        .changedTo(17, "3.0-milestone-1")
        .changedTo(21, "3.1-rc-1")
        .changedTo(23, "3.2-rc-1")
        .changedTo(24, "4.2-rc-1")
        .changedTo(31, "4.3-rc-1")
        .changedTo(36, "4.4-rc-1")
        .changedTo(48, "4.5-rc-1")
        .changedTo(51, "4.5.1")
        .changedTo(53, "4.6-rc-1")
        .changedTo(56, "4.7-rc-1")
        .changedTo(58, "4.8-rc-1")
        .changedTo(63, "4.10-rc-1")
        .changedTo(68, "5.0-milestone-1")
        .changedTo(69, "5.0-rc-1")
        .changedTo(71, "5.3-rc-1")
        .changedTo(79, "6.0-rc-1")
        .changedTo(82, "6.0-rc-2")
        .changedTo(95, "6.1-rc-1")
        .changedTo(96, "6.4-rc-1")
        .changedTo(97, "6.8-rc-1")
        .changedTo(99, "7.5-rc-1")
        .changedTo(101, "7.6.2")
        .changedToWithConflict(100, "8.0-milestone-5")
        .changedTo(105, "8.1-rc-2")
        .changedTo(106, "8.2-milestone-1")
    ),

    RESOURCES(ROOT, "resources", introducedIn("1.9-rc-1")),

    TRANSFORMS(null, "transforms", introducedIn("3.5-rc-1")
        .changedTo(2, "5.1")
        .changedTo(3, "6.8-rc-1")
        .changedTo(4, "8.5-rc-1")
    );

    private final String name;
    private final CacheVersionMapping versionMapping;

    CacheLayout(@Nullable CacheLayout parent, String name, CacheVersionMapping.Builder versionMappingBuilder) {
        this.name = name;
        this.versionMapping = parent == null ? versionMappingBuilder.build() : versionMappingBuilder.build(parent.getVersion());
    }

    public String getName() {
        return name;
    }

    public CacheVersion getVersion() {
        return versionMapping.getLatestVersion();
    }

    public String getKey() {
        String version = getVersion().toString().endsWith("4")
            ? getVersion() + "-dev"
            : getVersion().toString();
        return getName() + "-" + version;
    }

    public CacheVersionMapping getVersionMapping() {
        return versionMapping;
    }

    public File getPath(File parentDir) {
        return new File(parentDir, getKey());
    }
}
