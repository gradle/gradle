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
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.RepositoryAwareVerificationFailure;
import org.gradle.api.internal.artifacts.verification.verifier.DeletedArtifact;
import org.gradle.api.internal.artifacts.verification.verifier.MissingChecksums;
import org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure;
import org.gradle.api.internal.artifacts.verification.verifier.VerificationFailure;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.internal.Factory;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class DependencyVerificationReportWriter {
    private static final Comparator<Map.Entry<ModuleComponentArtifactIdentifier, Collection<RepositoryAwareVerificationFailure>>> DELETED_LAST = Comparator.comparing(e -> e.getValue().stream().anyMatch(f -> f.getFailure() instanceof DeletedArtifact) ? 1 : 0);
    private static final Comparator<Map.Entry<ModuleComponentArtifactIdentifier, Collection<RepositoryAwareVerificationFailure>>> MISSING_LAST = Comparator.comparing(e -> e.getValue().stream().anyMatch(f -> f.getFailure() instanceof MissingChecksums) ? 1 : 0);
    private static final Comparator<Map.Entry<ModuleComponentArtifactIdentifier, Collection<RepositoryAwareVerificationFailure>>> BY_MODULE_ID = Comparator.comparing(e -> e.getKey().getDisplayName());
    public static final String VERBOSE_CONSOLE = "org.gradle.dependency.verification.console";
    public static final String VERBOSE_VALUE = "verbose";

    private final Path gradleUserHome;
    private Runnable rendererInitializer;
    private AbstractTextDependencyVerificationReportRenderer summaryRenderer;
    private HtmlDependencyVerificationReportRenderer htmlRenderer;

    public DependencyVerificationReportWriter(Path gradleUserHome,
                                              DocumentationRegistry documentationRegistry,
                                              File verificationFile,
                                              List<String> writeFlags,
                                              File htmlReportOutputDirectory,
                                              Factory<GradleProperties> gradlePropertiesProvider) {
        this.gradleUserHome = gradleUserHome;
        this.rendererInitializer = () -> {
            this.summaryRenderer = createConsoleRenderer(gradleUserHome, documentationRegistry, gradlePropertiesProvider.create());
            this.htmlRenderer = new HtmlDependencyVerificationReportRenderer(documentationRegistry, verificationFile, writeFlags, htmlReportOutputDirectory);
        };
    }

    private static boolean isVerboseConsoleReport(GradleProperties gradleProperties) {
        String param = gradleProperties.find(VERBOSE_CONSOLE);
        return VERBOSE_VALUE.equals(param);
    }

    private AbstractTextDependencyVerificationReportRenderer createConsoleRenderer(Path gradleUserHome, DocumentationRegistry documentationRegistry, GradleProperties gradleProperties) {
        boolean verboseConsoleReport = isVerboseConsoleReport(gradleProperties);
        if (verboseConsoleReport) {
            return new TextDependencyVerificationReportRenderer(gradleUserHome, documentationRegistry);
        }
        return new SimpleTextDependencyVerificationReportRenderer(gradleUserHome, documentationRegistry);
    }

    public VerificationReport generateReport(String displayName,
                                             Multimap<ModuleComponentArtifactIdentifier, RepositoryAwareVerificationFailure> failuresByArtifact) {
        assertInitialized();
        // We need at least one fatal failure: if it's only "warnings" we don't care
        // but of there's a fatal failure AND a warning we want to show both
        doRender(displayName, failuresByArtifact, summaryRenderer);
        doRender(displayName, failuresByArtifact, htmlRenderer);
        File htmlReport = htmlRenderer.writeReport();
        return new VerificationReport(summaryRenderer.render(), htmlReport);
    }

    public synchronized void assertInitialized() {
        if (rendererInitializer != null) {
            rendererInitializer.run();
            rendererInitializer = null;
        }
    }

    public void doRender(String displayName, Multimap<ModuleComponentArtifactIdentifier, RepositoryAwareVerificationFailure> failuresByArtifact, DependencyVerificationReportRenderer renderer) {
        ReportState reportState = new ReportState();
        renderer.startNewSection(displayName);
        renderer.startArtifactErrors(() -> {
            // Sorting entries so that error messages are always displayed in a reproducible order
            failuresByArtifact.asMap()
                .entrySet()
                .stream()
                .sorted(DELETED_LAST.thenComparing(MISSING_LAST).thenComparing(BY_MODULE_ID))
                .forEachOrdered(entry -> {
                    ModuleComponentArtifactIdentifier key = entry.getKey();
                    Collection<RepositoryAwareVerificationFailure> failures = entry.getValue();
                    onArtifactFailure(renderer, reportState, key, failures);
                });
        });
        renderer.finish(reportState);
    }

    private void onArtifactFailure(DependencyVerificationReportRenderer renderer, ReportState state, ModuleComponentArtifactIdentifier key, Collection<RepositoryAwareVerificationFailure> failures) {
        failures.stream()
            .map(RepositoryAwareVerificationFailure::getFailure)
            .map(this::extractFailedFilePaths)
            .forEach(state::addAffectedFile);
        renderer.startNewArtifact(key, () -> {
            if (failures.size() == 1) {
                RepositoryAwareVerificationFailure firstFailure = failures.iterator().next();
                explainSingleFailure(renderer, state, firstFailure);
            } else {
                explainMultiFailure(renderer, state, failures);
            }
        });
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

    private void explainMultiFailure(DependencyVerificationReportRenderer renderer, ReportState state, Collection<RepositoryAwareVerificationFailure> failures) {
        renderer.reportAsMultipleErrors(() -> {
            for (RepositoryAwareVerificationFailure failure : failures) {
                explainSingleFailure(renderer, state, failure);
            }
        });
    }

    private void explainSingleFailure(DependencyVerificationReportRenderer renderer, ReportState state, RepositoryAwareVerificationFailure wrapper) {
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
        renderer.reportFailure(wrapper);
    }

}
