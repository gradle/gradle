/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.failure;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictException;
import org.gradle.internal.Pair;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.locking.LockOutOfDateException;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.internal.resolve.ModuleVersionNotFoundException;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.problems.buildtree.ProblemReporter;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Reports resolution failures to the user by writing detailed info to an HTML report, and throwing an exception containing
 * a quick summary.
 *
 * @since 7.5
 */
@ServiceScope(Scopes.BuildTree.class)
public final class ResolutionFailuresReporter implements ProblemReporter {
    private final ResolutionFailuresListener failuresListener;

    public ResolutionFailuresReporter(ResolutionFailuresListener failuresListener) {
        this.failuresListener = failuresListener;
    }

    @Override
    public String getId() {
        return "resolution failures";
    }

    @Override
    public void report(File reportDir, Consumer<? super Throwable> validationFailures) {
        List<Throwable> errors = getErrors();

        if (!errors.isEmpty()) {
            File reportFile = new File(reportDir, "reports/dependency-resolution/resolution-failure-report.html");
            writeHtmlReport(reportFile, errors);

            String summary = buildFailureSummary(errors, reportFile);
            validationFailures.accept(new GradleException(summary));
        }
    }

    private List<Throwable> getErrors() {
        return failuresListener.getErrors().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey() == null ? "" : e.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    private void writeHtmlReport(File reportFile, List<Throwable> errors) {
        HtmlResolutionFailureReport htmlReport = new HtmlResolutionFailureReport(reportFile);
        htmlReport.writeReport(errors);
    }

    private String buildFailureSummary(List<Throwable> errors, File reportFile) {
        String summarizedErrors = errors.stream()
                .map(this::summarizeResolutionError)
                .map(s -> "- " + s)
                .collect(Collectors.joining("\n"));
        String reportLink = String.format("See %s for details.", new ConsoleRenderer().asClickableFileUrl(reportFile));
        return String.format("Dependency resolution failed.\n\n%s\n\n%s", summarizedErrors, reportLink);
    }

    private String summarizeResolutionError(Throwable cause) {
        if (cause instanceof VersionConflictException) {
            return summarizeConflict((VersionConflictException) cause);
        } else if (cause instanceof LockOutOfDateException) {
            return summarizeOutOfDateLocks((LockOutOfDateException) cause);
        } else if (cause instanceof ResolveException) {
            return summarizeResolveFailure((ResolveException) cause);
        } else if (cause instanceof ModuleVersionNotFoundException) {
            return summarizeModuleVersionNotFoundMsg((ModuleVersionNotFoundException) cause);
        } else { // Fallback to failing the task in case we don't know anything special about the error
            throw UncheckedException.throwAsUncheckedException(cause);
        }
    }

    private String summarizeModuleVersionNotFoundMsg(ModuleVersionNotFoundException cause) {
        return String.format("Could not find module: %s.", cause.getSelector().toString());
    }

    private String summarizeResolveFailure(ResolveException cause) {
        return cause.getMessage();
    }

    private String summarizeOutOfDateLocks(final LockOutOfDateException cause) {
        return "The dependency locks are out-of-date.";
    }

    private String summarizeConflict(final VersionConflictException cause) {
        return cause.getConflicts().stream()
                .map(Pair::getRight)
                .collect(Collectors.joining(", ", "\"Dependency resolution failed because of conflict(s) on the following module(s): ", "."));
    }
}
