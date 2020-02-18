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
class TextDependencyVerificationReportRenderer implements DependencyVerificationReportRenderer {
    private TreeFormatter formatter;
    private final Path gradleUserHome;
    private final DocumentationRegistry documentationRegistry;

    private boolean inMultiErrors;

    public TextDependencyVerificationReportRenderer(Path gradleUserHome, DocumentationRegistry documentationRegistry) {
        this.gradleUserHome = gradleUserHome;
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public void title(String title) {
        formatter = new TreeFormatter();
        formatter.node("Dependency verification failed for " + title);
    }

    @Override
    public void withErrors(Runnable action) {
        formatter.startChildren();
        action.run();
        formatter.endChildren();
        formatter.blankLine();
    }

    @Override
    public void withArtifact(ModuleComponentArtifactIdentifier key, Runnable action) {
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

    private void legend(String legendItem) {
        formatter.node(legendItem);
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
    public void finish(DependencyVerificationReportWriter.HighLevelErrors highLevelErrors) {
        if (highLevelErrors.isMaybeCompromised()) {
            StringBuilder sb = new StringBuilder();
            sb.append("This can indicate that a dependency has been compromised. Please carefully verify the ");
            if (highLevelErrors.hasFailedSignatures()) {
                sb.append("signatures and ");
            }
            sb.append("checksums.");
            legend(sb.toString());
        }
        if (highLevelErrors.canSuggestWriteMetadata()) {
            // the else is just to avoid telling people to use `--write-verification-metadata` if we suspect compromised dependencies
            legend("If the artifacts are trustworthy, you will need to update the gradle/verification-metadata.xml file by following the instructions at " + documentationRegistry.getDocumentationFor("dependency_verification", "sec:troubleshooting-verification"));
        }
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

    @Override
    public void multipleErrors(Runnable action) {
        formatter.append("multiple problems reported:");
        formatter.startChildren();
        inMultiErrors = true;
        action.run();
        inMultiErrors = false;
        formatter.endChildren();
    }

    String render() {
        return formatter.toString();
    }
}
