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

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.RepositoryAwareVerificationFailure;
import org.gradle.api.internal.artifacts.verification.verifier.DeletedArtifact;
import org.gradle.api.internal.artifacts.verification.verifier.MissingChecksums;
import org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure;
import org.gradle.api.internal.artifacts.verification.verifier.VerificationFailure;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.logging.text.TreeFormatter;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

public class DependencyVerificationReportWriter {
    private static final Comparator<Map.Entry<ModuleComponentArtifactIdentifier, Collection<RepositoryAwareVerificationFailure>>> DELETED_LAST = Comparator.comparing(e -> e.getValue().stream().anyMatch(f -> f.getFailure() instanceof DeletedArtifact) ? 1 : 0);
    private static final Comparator<Map.Entry<ModuleComponentArtifactIdentifier, Collection<RepositoryAwareVerificationFailure>>> MISSING_LAST = Comparator.comparing(e -> e.getValue().stream().anyMatch(f -> f.getFailure() instanceof MissingChecksums) ? 1 : 0);
    private static final Comparator<Map.Entry<ModuleComponentArtifactIdentifier, Collection<RepositoryAwareVerificationFailure>>> BY_MODULE_ID = Comparator.comparing(e -> e.getKey().getDisplayName());

    private final Path gradleUserHome;
    private final DocumentationRegistry documentationRegistry;

    public DependencyVerificationReportWriter(Path gradleUserHome,
                                              DocumentationRegistry documentationRegistry) {
        this.gradleUserHome = gradleUserHome;
        this.documentationRegistry = documentationRegistry;
    }

    public Report generateReport(String displayName,
                               Multimap<ModuleComponentArtifactIdentifier, RepositoryAwareVerificationFailure> failuresByArtifact) {
        // We need at least one fatal failure: if it's only "warnings" we don't care
        // but of there's a fatal failure AND a warning we want to show both
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Dependency verification failed for " + displayName);
        formatter.startChildren();
        ReportState reportState = new ReportState();
        // Sorting entries so that error messages are always displayed in a reproducible order
        failuresByArtifact.asMap()
            .entrySet()
            .stream()
            .sorted(DELETED_LAST.thenComparing(MISSING_LAST).thenComparing(BY_MODULE_ID))
            .forEachOrdered(entry -> {
                ModuleComponentArtifactIdentifier key = entry.getKey();
                Collection<RepositoryAwareVerificationFailure> failures = entry.getValue();
                onArtifactFailure(formatter, reportState, key, failures);
            });
        formatter.endChildren();
        formatter.blankLine();
        if (reportState.isMaybeCompromised()) {
            formatter.node("This can indicate that a dependency has been compromised. Please carefully verify the ");
            if (reportState.hasFailedSignatures()) {
                formatter.append("signatures and ");
            }
            formatter.append("checksums.");
        }
        if (reportState.canSuggestWriteMetadata()) {
            // the else is just to avoid telling people to use `--write-verification-metadata` if we suspect compromised dependencies
            formatter.node("If the artifacts are trustworthy, you will need to update the gradle/verification-metadata.xml file by following the instructions at " + documentationRegistry.getDocumentationFor("dependency_verification", "sec:troubleshooting-verification"));
        }
        Set<String> affectedFiles = reportState.getAffectedFiles();
        if (!affectedFiles.isEmpty()) {
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
        return new Report(formatter.toString(), null);
    }

    public void onArtifactFailure(TreeFormatter formatter, ReportState state, ModuleComponentArtifactIdentifier key, Collection<RepositoryAwareVerificationFailure> failures) {
        failures.stream()
            .map(RepositoryAwareVerificationFailure::getFailure)
            .map(this::extractFailedFilePaths)
            .forEach(state::addAffectedFile);
        formatter.node("On artifact " + key + " ");
        if (failures.size() == 1) {
            RepositoryAwareVerificationFailure firstFailure = failures.iterator().next();
            explainSingleFailure(formatter, state, firstFailure);
        } else {
            explainMultiFailure(formatter, state, failures);
        }
    }

    private String extractFailedFilePaths(VerificationFailure f) {
        String shortenPath = shortenPath(f.getFilePath());
        if (f instanceof SignatureVerificationFailure) {
            File signatureFile = ((SignatureVerificationFailure) f).getSignatureFile();
            return shortenPath + " (signature: " + shortenPath(signatureFile) + ")";
        }
        return shortenPath;
    }

    // Shortens the path for display the user
    private String shortenPath(File file) {
        Path path = file.toPath();
        try {
            Path relativize = gradleUserHome.relativize(path);
            return "GRADLE_USER_HOME" + File.separator + relativize;
        } catch (IllegalArgumentException e) {
            return file.getAbsolutePath();
        }
    }

    private void explainMultiFailure(TreeFormatter formatter, ReportState state, Collection<RepositoryAwareVerificationFailure> failures) {
        formatter.append("multiple problems reported");
        formatter.startChildren();
        for (RepositoryAwareVerificationFailure failure : failures) {
            formatter.node("");
            explainSingleFailure(formatter, state, failure);
        }
        formatter.endChildren();
    }

    private void explainSingleFailure(TreeFormatter formatter, ReportState state, RepositoryAwareVerificationFailure wrapper) {
        VerificationFailure failure = wrapper.getFailure();
        if (failure instanceof MissingChecksums) {
            state.hasMissing();
        } else {
            if (failure instanceof SignatureVerificationFailure) {
                state.failedSignatures();
                if (((SignatureVerificationFailure) failure).getErrors().values().stream().map(SignatureVerificationFailure.SignatureError::getKind).noneMatch(kind -> kind == SignatureVerificationFailure.FailureKind.PASSED_NOT_TRUSTED)) {
                    state.maybeCompromised();
                } else {
                    state.hasUntrustedKeys();
                }
            } else {
                state.maybeCompromised();
            }
        }
        formatter.append("in repository '" + wrapper.getRepositoryName() + "': ");
        failure.explainTo(formatter);
    }

    private static class ReportState {
        private final Set<String> affectedFiles = Sets.newTreeSet();
        private boolean maybeCompromised;
        private boolean hasMissing;
        private boolean failedSignatures;
        private boolean hasUntrustedKeys;

        public void maybeCompromised() {
            maybeCompromised = true;
        }

        public void hasMissing() {
            hasMissing = true;
        }

        public void failedSignatures() {
            failedSignatures = true;
        }

        public void hasUntrustedKeys() {
            hasUntrustedKeys = true;
        }

        public boolean isMaybeCompromised() {
            return maybeCompromised;
        }

        public boolean isHasMissing() {
            return hasMissing;
        }

        public boolean hasFailedSignatures() {
            return failedSignatures;
        }

        public boolean isHasUntrustedKeys() {
            return hasUntrustedKeys;
        }

        public boolean canSuggestWriteMetadata() {
            return (hasMissing || hasUntrustedKeys) && !maybeCompromised;
        }

        public Set<String> getAffectedFiles() {
            return affectedFiles;
        }

        public void addAffectedFile(String file) {
            affectedFiles.add(file);
        }
    }

    public static class Report {
        private final String summary;
        private final File htmlReport;

        public Report(String summary, File htmlReport) {
            this.summary = summary;
            this.htmlReport = htmlReport;
        }

        public String getSummary() {
            return summary;
        }

        public File getHtmlReport() {
            return htmlReport;
        }
    }
}
