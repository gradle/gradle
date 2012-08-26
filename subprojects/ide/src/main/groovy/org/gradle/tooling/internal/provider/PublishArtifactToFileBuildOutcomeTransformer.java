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

package org.gradle.tooling.internal.provider;

import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.tasks.bundling.*;
import org.gradle.plugins.ear.Ear;
import org.gradle.tooling.internal.migration.DefaultFileBuildOutcome;
import org.gradle.tooling.model.internal.migration.FileBuildOutcome;

import java.util.Set;

import static org.gradle.tooling.internal.provider.FileOutcomeIdentifier.*;

public class PublishArtifactToFileBuildOutcomeTransformer implements Transformer<FileBuildOutcome, PublishArtifact> {

    public FileBuildOutcome transform(PublishArtifact artifact) {
        String taskPath = getTaskPath(artifact);
        String typeIdentifier = getTypeIdentifier(artifact);
        return new DefaultFileBuildOutcome(artifact.getFile(), typeIdentifier, taskPath);
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
        String taskPath = null;
        Set<? extends Task> tasks = artifact.getBuildDependencies().getDependencies(null);
        if (!tasks.isEmpty()) {
            taskPath = tasks.iterator().next().getPath();
        }
        return taskPath;
    }

}
