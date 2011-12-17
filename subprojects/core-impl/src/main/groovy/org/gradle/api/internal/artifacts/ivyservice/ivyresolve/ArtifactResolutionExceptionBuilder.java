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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.util.GUtil;

import java.util.ArrayList;
import java.util.List;

public class ArtifactResolutionExceptionBuilder {
    private final Artifact artifact;
    private final List<ArtifactResolveException> downloadFailures = new ArrayList<ArtifactResolveException>();

    public ArtifactResolutionExceptionBuilder(Artifact artifact) {
        this.artifact = artifact;
    }

    public static ArtifactResolveException downloadFailure(Artifact artifact, String failureMessage) {
        String message = String.format("Download failed for artifact '%s': %s", render(artifact), failureMessage);
        return new ArtifactResolveException(message);
    }

    public void addDownloadFailure(ArtifactResolveException downloadFailure) {
        downloadFailures.add(downloadFailure);
    }

    public ArtifactResolveException buildException() {
        switch (downloadFailures.size()) {
            case 0:
                return notFound(artifact);
            case 1:
                return downloadFailures.get(0);
            default:
                // TODO:DAZ Handle multiple failures
                return downloadFailures.get(0);
        }
    }

    private ArtifactNotFoundException notFound(Artifact artifact) {
        String message = String.format("Artifact '%s' not found", render(artifact));
        return new ArtifactNotFoundException(message);
    }

    private static String render(Artifact artifact) {
        ModuleRevisionId moduleRevisionId = artifact.getId().getModuleRevisionId();
        StringBuilder builder = new StringBuilder();
        builder.append(moduleRevisionId.getOrganisation())
                .append(":").append(moduleRevisionId.getName())
                .append(":").append(moduleRevisionId.getRevision());
        String classifier = artifact.getExtraAttribute("classifier");
        if (GUtil.isTrue(classifier)) {
            builder.append(":").append(classifier);
        }
        builder.append("@").append(artifact.getExt());
        return builder.toString();
    }    
}
