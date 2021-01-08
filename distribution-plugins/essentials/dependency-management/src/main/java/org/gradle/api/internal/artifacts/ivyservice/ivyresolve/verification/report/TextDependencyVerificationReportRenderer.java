/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.report;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.RepositoryAwareVerificationFailure;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.logging.text.TreeFormatter;

import java.nio.file.Path;
import java.util.Set;

/**
 * A text report renderer, which is <i>not</i> cumulative.
 */
class TextDependencyVerificationReportRenderer extends AbstractTextDependencyVerificationReportRenderer {

    private boolean inMultiErrors;

    public TextDependencyVerificationReportRenderer(Path gradleUserHome, DocumentationRegistry documentationRegistry) {
        super(gradleUserHome, documentationRegistry);
    }

    @Override
    public void startNewSection(String title) {
        formatter = new TreeFormatter();
        formatter.node("Dependency verification failed for " + title);
    }

    @Override
    public void startArtifactErrors(Runnable action) {
        formatter.startChildren();
        action.run();
        formatter.endChildren();
        formatter.blankLine();
    }

    @Override
    public void startNewArtifact(ModuleComponentArtifactIdentifier key, Runnable action) {
        formatter.node("On artifact " + key + " ");
        action.run();
    }

    @Override
    public void reportFailure(RepositoryAwareVerificationFailure failure) {
        if (inMultiErrors) {
            formatter.node("");
        }
        formatter.append("in repository '" + failure.getRepositoryName() + "': ");
        failure.getFailure().explainTo(formatter);
    }

    @Override
    public void reportAsMultipleErrors(Runnable action) {
        formatter.append("multiple problems reported:");
        formatter.startChildren();
        inMultiErrors = true;
        action.run();
        inMultiErrors = false;
        formatter.endChildren();
    }

    private void processAffectedFiles(Set<String> affectedFiles) {
        formatter.blankLine();
        formatter.node("These files failed verification:");
        formatter.startChildren();
        for (String affectedFile : affectedFiles) {
            formatter.node(affectedFile);
        }
        formatter.endChildren();
        formatter.blankLine();
        formatter.node("GRADLE_USER_HOME = " + gradleUserHome);
    }

    @Override
    public void finish(VerificationHighLevelErrors highLevelErrors) {
        super.finish(highLevelErrors);
        Set<String> affectedFiles = highLevelErrors.getAffectedFiles();
        if (!affectedFiles.isEmpty()) {
            processAffectedFiles(affectedFiles);
        }
        formatter.blankLine();
        formatter.node("These files failed verification:");
        formatter.startChildren();
        for (String affectedFile : highLevelErrors.getAffectedFiles()) {
            formatter.node(affectedFile);
        }
        formatter.endChildren();
        formatter.blankLine();
        formatter.node("GRADLE_USER_HOME = " + gradleUserHome);
    }

}
