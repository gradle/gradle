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

package org.gradle.api.plugins.buildcomparison.outcome.internal.tooling;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.tasks.bundling.*;
import org.gradle.plugins.ear.Ear;
import org.gradle.tooling.model.internal.outcomes.GradleFileBuildOutcome;

import java.net.URI;
import java.util.Set;

import static org.gradle.api.plugins.buildcomparison.outcome.internal.FileOutcomeIdentifier.*;

public class PublishArtifactToFileBuildOutcomeTransformer {

    public GradleFileBuildOutcome transform(PublishArtifact artifact, Project project) {
        String id = getId(artifact, project);
        String taskPath = getTaskPath(artifact);
        String description = getDescription(artifact);
        String typeIdentifier = getTypeIdentifier(artifact);

        return new DefaultGradleFileBuildOutcome(id, description, taskPath, artifact.getFile(), typeIdentifier);
    }

    private String getId(PublishArtifact artifact, Project project) {
        // Assume that each artifact points to a unique file, and use the relative path from the project as the id
        URI artifactUri = artifact.getFile().toURI();
        URI projectDirUri = project.getProjectDir().toURI();
        URI relativeUri = projectDirUri.relativize(artifactUri);
        return relativeUri.getPath();
    }

    private String getDescription(PublishArtifact artifact) {
        return String.format("Publish artifact '%s'", artifact.toString());
    }

    private String getTypeIdentifier(PublishArtifact artifact) {
        if (artifact instanceof ArchivePublishArtifact) {
            ArchivePublishArtifact publishArtifact = (ArchivePublishArtifact) artifact;
            AbstractArchiveTask task = publishArtifact.getArchiveTask();

            // There is an inheritance hierarchy in play here, so the order
            // of the clauses is very important.

            if (task instanceof War) {
                return WAR_ARTIFACT.getTypeIdentifier();
            } else if (task instanceof Ear) {
                return EAR_ARTIFACT.getTypeIdentifier();
            } else if (task instanceof Jar) {
                return JAR_ARTIFACT.getTypeIdentifier();
            } else if (task instanceof Zip) {
                return ZIP_ARTIFACT.getTypeIdentifier();
            } else if (task instanceof Tar) {
                return TAR_ARTIFACT.getTypeIdentifier();
            } else {
                // we don't know about this kind of archive task
                return ARCHIVE_ARTIFACT.getTypeIdentifier();
            }
        } else {
            // This could very well be a zip (or something else we understand), but we can't know for sure.
            // The client may try to infer from the file extension.
            return UNKNOWN_ARTIFACT.getTypeIdentifier();
        }
    }

    private String getTaskPath(PublishArtifact artifact) {
        if (artifact instanceof ArchivePublishArtifact) {
            return ((ArchivePublishArtifact) artifact).getArchiveTask().getPath();
        } else {
            String taskPath = null;
            Set<? extends Task> tasks = artifact.getBuildDependencies().getDependencies(null);
            if (!tasks.isEmpty()) {
                taskPath = tasks.iterator().next().getPath();
            }
            return taskPath;
        }
    }

}
