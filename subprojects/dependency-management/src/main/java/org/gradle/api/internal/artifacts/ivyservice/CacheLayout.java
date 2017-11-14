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

import org.gradle.util.VersionNumber;

import java.io.File;

public enum CacheLayout {
    ROOT(null, "modules", 2),
    FILE_STORE(ROOT, "files", 1),
    META_DATA(ROOT, "metadata", 36),
    RESOURCES(ROOT, "resources", 1),
    TRANSFORMS(null, "transforms", 1),
    TRANSFORMS_META_DATA(TRANSFORMS, "metadata", 1),
    TRANSFORMS_STORE(TRANSFORMS, "files", 1);

    // If you update the META_DATA version, also update DefaultGradleDistribution.getArtifactCacheLayoutVersion() (which is the historical record)
    // If you update FILE_STORE, you may also need to update LocallyAvailableResourceFinderFactory

    private final String name;
    private final CacheLayout parent;
    private final int version;

    CacheLayout(CacheLayout parent, String name, int version) {
        this.parent = parent;
        this.name = name;
        this.version = version;
    }

    public VersionNumber getVersion() {
        return VersionNumber.parse(getFormattedVersion());
    }

    public String getKey() {
        StringBuilder key = new StringBuilder();
        key.append(name);
        key.append("-");
        key.append(getFormattedVersion());
        return key.toString();
    }

    public String getFormattedVersion() {
        if (parent == null) {
            return String.valueOf(version);
        }
        return parent.getFormattedVersion() + '.' + String.valueOf(version);
    }

    public File getPath(File parentDir) {
        return new File(parentDir, getKey());
    }
}
