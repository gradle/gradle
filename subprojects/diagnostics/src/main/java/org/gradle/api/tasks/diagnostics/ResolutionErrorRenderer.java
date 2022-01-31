/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.tasks.diagnostics;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.file.Directory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictException;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.diagnostics.internal.dependencies.HtmlResolutionErrorRenderer;
import org.gradle.internal.Pair;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.locking.LockOutOfDateException;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

class ResolutionErrorRenderer implements Action<Throwable> {
    private final Spec<DependencyResult> dependencySpec;
    private final List<ErrorAction> errorActions = Lists.newArrayListWithExpectedSize(1);

    public ResolutionErrorRenderer(Spec<DependencyResult> dependencySpec) {
        this.dependencySpec = dependencySpec;
    }

    @Override
    public void execute(Throwable throwable) {
        if (throwable instanceof ResolveException) {
            Throwable cause = throwable.getCause();
            handleResolutionError(cause);
        } else {
            throw UncheckedException.throwAsUncheckedException(throwable);
        }
    }

    private void handleResolutionError(Throwable cause) {
        if (cause instanceof VersionConflictException) {
            handleConflict((VersionConflictException) cause);
        } else if (cause instanceof LockOutOfDateException) {
            handleOutOfDateLocks((LockOutOfDateException) cause);
        } else {
            // Fallback to failing the task in case we don't know anything special
            // about the error
            throw UncheckedException.throwAsUncheckedException(cause);
        }
    }

    private void handleOutOfDateLocks(final LockOutOfDateException cause) {
        registerError(cause, output -> {
            List<String> errors = cause.getErrors();
            output.text("The dependency locks are out-of-date:");
            output.println();
            for (String error : errors) {
                output.text("   - " + error);
                output.println();
            }
            output.println();
        });
    }

    private void handleConflict(final VersionConflictException conflict) {
        registerError(conflict, output -> {
            output.text("Dependency resolution failed because of conflict(s) on the following module(s):");
            output.println();
            for (Pair<List<? extends ModuleVersionIdentifier>, String> identifierStringPair : conflict.getConflicts()) {
                boolean matchesSpec = hasVersionConflictOnRequestedDependency(identifierStringPair.getLeft());
                if (!matchesSpec) {
                    continue;
                }
                output.text("   - ");
                output.withStyle(StyledTextOutput.Style.Error).text(identifierStringPair.getRight());
                output.println();
            }
            output.println();
        });

    }

    public void renderErrors(StyledTextOutput output, Directory outputDirectory) {
        for (Action<StyledTextOutput> errorAction : errorActions) {
            errorAction.execute(output);
        }

        final File reportFile = outputDirectory.file("resolution-errors.html").getAsFile();
        try (final FileWriter writer = new FileWriter(reportFile)) {
            final HtmlResolutionErrorRenderer htmlRenderer = new HtmlResolutionErrorRenderer();
            final List<Throwable> errors = errorActions.stream().map(a -> a.getException()).collect(Collectors.toList());
            htmlRenderer.render(errors, writer);
        } catch (IOException e) {
            throw new GradleException("Error creating report file: '" + reportFile + "'", e);
        }
    }

    private void registerError(Throwable exception, Action<StyledTextOutput> errorAction) {
        errorActions.add(new ErrorAction(exception, errorAction));
    }

    private boolean hasVersionConflictOnRequestedDependency(final List<? extends ModuleVersionIdentifier> versionIdentifiers) {
        for (final ModuleVersionIdentifier versionIdentifier : versionIdentifiers) {
            if (dependencySpec.isSatisfiedBy(asDependencyResult(versionIdentifier))) {
                return true;
            }
        }
        return false;
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

    private static final class ErrorAction implements Action<StyledTextOutput> {
        private final Throwable exception;
        private final Action<StyledTextOutput> delegate;

        private ErrorAction(Throwable exception, Action<StyledTextOutput> delegate) {
            this.exception = exception;
            this.delegate = delegate;
        }

        public Throwable getException() {
            return exception;
        }

        @Override
        public void execute(StyledTextOutput styledTextOutput) {
            delegate.execute(styledTextOutput);
        }
    }
}
