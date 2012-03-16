/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.resolutioncache;

import java.io.File;
import java.io.Serializable;
import java.util.Date;

class DefaultCachedArtifactResolution implements CachedArtifactResolution, Serializable {
    private final File artifactFile;
    private final long ageMillis;
    private final Date artifactLastModified;
    private final String artifactUrl;

    public DefaultCachedArtifactResolution(CachedArtifactResolutionIndexEntry entry, long ageMillis,
                                           Date artifactLastModified, String artifactUrl) {
        this.artifactFile = entry.artifactFile;
        this.ageMillis = ageMillis;
        this.artifactLastModified = artifactLastModified;
        this.artifactUrl = artifactUrl;
    }

    DefaultCachedArtifactResolution(long ageMillis) {
        this.ageMillis = ageMillis;

        this.artifactFile = null;
        this.artifactLastModified = null;
        this.artifactUrl = null;
    }

    public boolean isMissing() {
        return artifactFile == null;
    }

    public File getArtifactFile() {
        return artifactFile;
    }

    public long getAgeMillis() {
        return ageMillis;
    }

    public Date getArtifactLastModified() {
        return artifactLastModified;
    }

    public String getArtifactUrl() {
        return artifactUrl;
    }
}
