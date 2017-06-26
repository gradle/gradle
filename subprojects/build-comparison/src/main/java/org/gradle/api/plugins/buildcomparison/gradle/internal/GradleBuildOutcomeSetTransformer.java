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
import org.gradle.api.plugins.buildcomparison.outcome.internal.FileOutcomeIdentifier;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.tooling.model.internal.outcomes.GradleFileBuildOutcome;
import org.gradle.tooling.model.internal.outcomes.GradleBuildOutcome;
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * Transforms from the Gradle specific build outcomes into source agnostic outcomes.
 */
public class GradleBuildOutcomeSetTransformer implements Transformer<Set<BuildOutcome>, ProjectOutcomes> {

    private final FileStore<String> fileStore;
    private final String fileStorePrefix;

    private final List<String> zipArchiveTypes = Arrays.asList(
            FileOutcomeIdentifier.JAR_ARTIFACT.getTypeIdentifier(),
            FileOutcomeIdentifier.EAR_ARTIFACT.getTypeIdentifier(),
            FileOutcomeIdentifier.WAR_ARTIFACT.getTypeIdentifier(),
            FileOutcomeIdentifier.ZIP_ARTIFACT.getTypeIdentifier()
    );

    public GradleBuildOutcomeSetTransformer(FileStore<String> fileStore, String fileStorePrefix) {
        this.fileStore = fileStore;
        this.fileStorePrefix = fileStorePrefix;
    }

    public Set<BuildOutcome> transform(ProjectOutcomes rootProject) {
        Set<BuildOutcome> keyedOutcomes = new HashSet<BuildOutcome>();
        addBuildOutcomes(rootProject, rootProject, keyedOutcomes);
        return keyedOutcomes;
    }

    private void addBuildOutcomes(ProjectOutcomes projectOutcomes, ProjectOutcomes rootProject, Set<BuildOutcome> buildOutcomes) {
        for (GradleBuildOutcome outcome : projectOutcomes.getOutcomes()) {
            if (outcome instanceof GradleFileBuildOutcome) {
                addFileBuildOutcome((GradleFileBuildOutcome) outcome, rootProject, buildOutcomes);
            } else {
                new UnknownBuildOutcome(outcome.getTaskPath(), outcome.getDescription());
            }
        }

        for (ProjectOutcomes childProject : projectOutcomes.getChildren()) {
            addBuildOutcomes(childProject, rootProject, buildOutcomes);
        }
    }

    private void addFileBuildOutcome(GradleFileBuildOutcome outcome, ProjectOutcomes rootProject, Set<BuildOutcome> translatedOutcomes) {
        if (zipArchiveTypes.contains(outcome.getTypeIdentifier())) {
            File originalFile = outcome.getFile();
            String relativePath = GFileUtils.relativePath(rootProject.getProjectDirectory(), originalFile);

            LocallyAvailableResource resource = null;
            if (originalFile.exists()) {
                String filestoreDestination = fileStorePrefix + "/" + outcome.getTaskPath() + "/" + originalFile.getName();
                resource = fileStore.move(filestoreDestination, originalFile);
            }

            BuildOutcome buildOutcome = new GeneratedArchiveBuildOutcome(outcome.getTaskPath(), outcome.getDescription(), resource, relativePath);
            translatedOutcomes.add(buildOutcome);
        } else {
            String outcomeName = outcome.getTaskPath();
            if (isEmpty(outcomeName)) {
                outcomeName = GFileUtils.relativePath(rootProject.getProjectDirectory(), outcome.getFile());
            }
            translatedOutcomes.add(new UnknownBuildOutcome(outcomeName, outcome.getDescription()));
        }
    }

}
