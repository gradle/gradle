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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictException;
import org.gradle.internal.Pair;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.locking.LockOutOfDateException;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.problems.buildtree.ProblemReporter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@ServiceScope(Scopes.BuildTree.class)
public class ResolutionFailuresReporter implements ProblemReporter {
    private final ServiceRegistry services;
    private ResolvableDependencies currentResolution = null;

    public ResolutionFailuresReporter(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public String getId() {
        return "resolution failures";
    }

    // This method exists for THIS class to REPORT any additional problems it knows about to the given consumer
    @Override
    public void report(File reportDir, Consumer<? super Throwable> validationFailures) {
        File reportFile = new File(reportDir, "reports/dependency-resolution/resolution-failure-report.html");
        try {
            reportFile.getParentFile().mkdirs();
            reportFile.createNewFile();
        } catch (IOException e) {
            throw new GradleException("Error creating report file: '" + reportFile + "'", e);
        }

        try (final FileWriter writer = new FileWriter(reportFile)) {
            final HtmlResolutionErrorRenderer htmlRenderer = new HtmlResolutionErrorRenderer();
            htmlRenderer.render(getErrors(), writer);
        } catch (IOException e) {
            throw new GradleException("Error writing report file: '" + reportFile + "'", e);
        }
    }

    private List<Throwable> getErrors() {
        ResolutionFailuresListener failuresListener = services.get(ResolutionFailuresListener.class);
        return failuresListener.getErrors().entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().getName())).map(Map.Entry::getValue).collect(Collectors.toList());
    }

    public void renderErrorsToConsole(StyledTextOutput output) {
        getErrors().forEach(e -> renderResolutionError(e, output));
    }

    private void renderResolutionError(Throwable cause, StyledTextOutput output) {
        if (cause instanceof VersionConflictException) {
            renderConflict((VersionConflictException) cause, output);
        } else if (cause instanceof LockOutOfDateException) {
            renderOutOfDateLocks((LockOutOfDateException) cause, output);
        } else {
            // Fallback to failing the task in case we don't know anything special
            // about the error
            throw UncheckedException.throwAsUncheckedException(cause);
        }
    }

    private void renderOutOfDateLocks(final LockOutOfDateException cause, StyledTextOutput output) {
        List<String> errors = cause.getErrors();
        output.text("The dependency locks are out-of-date:");
        output.println();
        for (String error : errors) {
            output.text("   - " + error);
            output.println();
        }
        output.println();
    }

    private void renderConflict(final VersionConflictException conflict, StyledTextOutput output) {
        output.text("Dependency resolution failed because of conflict(s) on the following module(s):");
        output.println();
        for (Pair<List<? extends ModuleVersionIdentifier>, String> identifierStringPair : conflict.getConflicts()) {
            output.text("   - ");
            output.withStyle(StyledTextOutput.Style.Error).text(identifierStringPair.getRight());
            output.println();
        }
        output.println();
    }

    private DependencyResult asDependencyResult(final ModuleVersionIdentifier versionIdentifier) {
        return new DependencyResult() {
            @Override
            public ComponentSelector getRequested() {
                return DefaultModuleComponentSelector.newSelector(versionIdentifier.getModule(), versionIdentifier.getVersion());
            }

            @Override
            public ResolvedComponentResult getFrom() {
                return null;
            }

            @Override
            public boolean isConstraint() {
                return false;
            }
        };
    }
}
