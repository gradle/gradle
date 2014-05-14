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

package org.gradle.api.plugins.buildcomparison.gradle.internal;

import org.gradle.api.Transformer;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcome;
import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.GeneratedArchiveBuildOutcome;
import org.gradle.api.plugins.buildcomparison.outcome.internal.unknown.UnknownBuildOutcome;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class GradleBuildOutcomeSetInferrer implements Transformer<Set<BuildOutcome>, Set<BuildOutcome>> {

    private final FileStore<String> fileStore;
    private final String fileStorePrefix;
    private final File baseDir;

    public GradleBuildOutcomeSetInferrer(FileStore<String> fileStore, String fileStorePrefix, File baseDir) {
        this.fileStore = fileStore;
        this.fileStorePrefix = fileStorePrefix;
        this.baseDir = baseDir;
    }

    public Set<BuildOutcome> transform(Set<BuildOutcome> sourceOutcomes) {
        return CollectionUtils.collect(sourceOutcomes, new HashSet<BuildOutcome>(sourceOutcomes.size()), new Transformer<BuildOutcome, BuildOutcome>() {
            public BuildOutcome transform(BuildOutcome original) {
                return infer(original);
            }
        });
    }

    private BuildOutcome infer(BuildOutcome outcome) {
        if (outcome instanceof UnknownBuildOutcome) {
            return new UnknownBuildOutcome(outcome.getName(), outcome.getDescription());
        } else if (outcome instanceof GeneratedArchiveBuildOutcome) {
            GeneratedArchiveBuildOutcome archiveBuildOutcome = (GeneratedArchiveBuildOutcome) outcome;
            File file = new File(baseDir, archiveBuildOutcome.getRootRelativePath());
            String rootRelativePath = archiveBuildOutcome.getRootRelativePath();

            // TODO - we are relying on knowledge that the name of the outcome is the task path
            String taskPath = outcome.getName();

            LocallyAvailableResource resource = null;
            if (file.exists()) {
                String filestoreDestination = String.format("%s/%s/%s", fileStorePrefix, taskPath, file.getName());
                resource = fileStore.move(filestoreDestination, file);
            }

            return new GeneratedArchiveBuildOutcome(outcome.getName(), outcome.getDescription(), resource, rootRelativePath);
        } else {
            throw new IllegalStateException(String.format("Unhandled build outcome type: %s", outcome.getClass().getName()));
        }
    }
}
