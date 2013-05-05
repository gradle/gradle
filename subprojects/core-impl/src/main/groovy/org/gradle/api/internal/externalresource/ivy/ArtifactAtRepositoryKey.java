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

package org.gradle.api.internal.externalresource.ivy;

import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionRepository;

public class ArtifactAtRepositoryKey {
    private final String repositoryId;
    private final String artifactId;

    public ArtifactAtRepositoryKey(ModuleVersionRepository repository, ArtifactRevisionId artifactId) {
        this(repository, getArtifactKey(artifactId));
    }

    public ArtifactAtRepositoryKey(String repositoryId, String artifactId) {
        this.repositoryId = repositoryId;
        this.artifactId = artifactId;
    }

    private ArtifactAtRepositoryKey(ModuleVersionRepository repository, String artifactPath) {
        this.repositoryId = repository.getId();
        this.artifactId = artifactPath;
    }

    private static String getArtifactKey(ArtifactRevisionId artifactId) {
        String format = "[organisation]/[module](/[branch])/[revision]/[type]/[artifact](-[classifier])(.[ext])";
        Artifact dummyArtifact = new DefaultArtifact(artifactId, null, null, false);
        return IvyPatternHelper.substitute(format, dummyArtifact);
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getRepositoryId() {
        return repositoryId;
    }

    @Override
    public String toString() {
        return repositoryId + ":" + artifactId;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof ArtifactAtRepositoryKey)) {
            return false;
        }
        ArtifactAtRepositoryKey other = (ArtifactAtRepositoryKey) o;
        return toString().equals(other.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
