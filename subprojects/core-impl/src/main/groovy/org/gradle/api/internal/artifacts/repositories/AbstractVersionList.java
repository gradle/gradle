/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories;

import org.apache.ivy.plugins.latest.ArtifactInfo;
import org.apache.ivy.plugins.latest.LatestStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

abstract class AbstractVersionList implements VersionList {
    public boolean isEmpty() {
        return getVersionStrings().isEmpty();
    }

    public List<String> sortLatestFirst(LatestStrategy latestStrategy) {
        List<String> versions = new ArrayList<String>(getVersionStrings());
        ArtifactInfo[] artifactInfos = new ArtifactInfo[versions.size()];
        for (int i = 0; i < versions.size(); i++) {
            String version = versions.get(i);
            artifactInfos[i] = new VersionArtifactInfo(version);
        }
        List<ArtifactInfo> sorted = latestStrategy.sort(artifactInfos);
        Collections.reverse(sorted);

        List<String> sortedVersions = new ArrayList<String>();
        for (ArtifactInfo info : sorted) {
            sortedVersions.add(info.getRevision());
        }
        return sortedVersions;
    }

    private class VersionArtifactInfo implements ArtifactInfo {
        private final String version;

        private VersionArtifactInfo(String version) {
            this.version = version;
        }

        public String getRevision() {
            return version;
        }

        public long getLastModified() {
            return 0;
        }
    }
}
