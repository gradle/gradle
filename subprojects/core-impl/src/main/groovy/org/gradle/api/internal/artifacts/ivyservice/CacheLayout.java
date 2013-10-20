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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public enum CacheLayout {
    ROOT("modules", "%d", 1, null),
    FILE_STORE("files", "%d.%d", ROOT.majorVersion, 1),
    META_DATA("metadata", "%d.%d", ROOT.majorVersion, 31);

    private final String name;
    private final String versionPattern;
    private final Integer majorVersion;
    private final Integer minorVersion;

    private CacheLayout(String name, String versionPattern, Integer majorVersion, Integer minorVersion) {
        this.name = name;
        this.versionPattern = versionPattern;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }

    public Integer getMajorVersion() {
        return majorVersion;
    }

    public Integer getMinorVersion() {
        return minorVersion;
    }

    public String getKey() {
        StringBuilder key = new StringBuilder();
        key.append(name);
        key.append("-");
        key.append(getFormattedVersion());
        return key.toString();
    }

    public String getFormattedVersion() {
        List<Integer> versions = new ArrayList<Integer>();
        versions.add(majorVersion);

        if(minorVersion != null) {
            versions.add(minorVersion);
        }

        return String.format(versionPattern, versions.toArray());
    }

    public File getPath(File parentDir) {
        return new File(parentDir, getKey());
    }
}