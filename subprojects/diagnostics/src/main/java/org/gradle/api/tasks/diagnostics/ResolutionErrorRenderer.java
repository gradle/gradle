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

import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.Conflict;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictException;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.locking.LockOutOfDateException;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutput.Style;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.gradle.util.internal.TextUtil.getPluralEnding;

class ResolutionErrorRenderer {
    private final Spec<DependencyResult> dependencySpec;
    private final List<Action<StyledTextOutput>> errorActions = new ArrayList<>(1);
    private final List<Provider<List<Throwable>>> errorSources = new ArrayList<>(1);

    public ResolutionErrorRenderer(@Nullable Spec<DependencyResult> dependencySpec) {
        this.dependencySpec = dependencySpec;
    }

    public void addErrorSource(Provider<List<Throwable>> errorSource) {
        errorSources.add(errorSource);
    }

    private void resolveErrorProviders() {
        errorSources.stream().flatMap(x -> x.get().stream()).forEach(failure -> {
            if (failure instanceof ResolveException) {
                ((ResolveException) failure).getCauses().forEach(this::handleResolutionError);
            } else {
                handleResolutionError(failure);
            }
        });
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
        registerError(output -> {
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
        registerError(output -> {
            Collection<Conflict> conflicts = conflict.getConflicts();
            String plural = getPluralEnding(conflicts);
            output.text("Dependency resolution failed because of conflict" + plural + " on the following module"+ plural + ":");
            output.println();
            conflicts.stream()
                .filter(idendifierStringPair -> hasVersionConflictOnRequestedDependency(idendifierStringPair.getVersions()))
                .forEach(identifierStringPair -> {
                    output.text("   - ");
                    output.withStyle(Style.Error).text(identifierStringPair.getMessage());
                    output.println();
                });
            output.println();
        });

    }

    public void renderErrors(StyledTextOutput output) {
        resolveErrorProviders();
        for (Action<StyledTextOutput> errorAction : errorActions) {
            errorAction.execute(output);
        }
    }

    private void registerError(Action<StyledTextOutput> errorAction) {
        errorActions.add(errorAction);
    }

    private boolean hasVersionConflictOnRequestedDependency(final Collection<? extends ModuleVersionIdentifier> versionIdentifiers) {
        Objects.requireNonNull(dependencySpec, "Dependency spec must be specified");
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

}
