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
package org.gradle.api.internal.artifacts.ivyservice.artifactcache;

import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionRepository;
import org.gradle.util.UncheckedException;
import org.jfrog.wharf.ivy.util.WharfUtils;

import java.io.File;
import java.io.IOException;

public class LinkingArtifactFileStore implements ArtifactFileStore {
    private static final String DEFAULT_ARTIFACT_PATTERN =
            "[organisation]/[module](/[branch])/[revision]/[type]/[artifact]-[revision](-[classifier])(.[ext])";

    private File baseDir;

    public LinkingArtifactFileStore(File baseDir) {
        this.baseDir = baseDir;
    }

    public File storeArtifactFile(ModuleVersionRepository repository, ArtifactRevisionId artifactId, File contentFile) {
        File cacheFile = getArtifactFileLocation(repository, artifactId);
        try {
            WharfUtils.linkCacheFileToStorage(contentFile, cacheFile);
        } catch (IOException e) {
            throw UncheckedException.asUncheckedException(e);
        }
        return cacheFile;
    }

    public void removeArtifactFile(ModuleVersionRepository repository, ArtifactRevisionId artifactId) {
        File cacheFile = getArtifactFileLocation(repository, artifactId);
        cacheFile.delete();
    }
    
    public String getArtifactPath(ArtifactRevisionId artifactId) {
        Artifact dummyArtifact = new DefaultArtifact(artifactId, null, null, false);
        return IvyPatternHelper.substitute(DEFAULT_ARTIFACT_PATTERN, dummyArtifact);
    }

    private File getArtifactFileLocation(ModuleVersionRepository repository, ArtifactRevisionId artifactId) {
        String resolverId = repository.getId();
        String artifactPath = getArtifactPath(artifactId);
        return new File(baseDir, resolverId + "/" + artifactPath);
    }
}
