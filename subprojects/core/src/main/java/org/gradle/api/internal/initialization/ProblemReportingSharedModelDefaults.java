/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.initialization;

import kotlin.Unit;
import org.gradle.api.Action;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.initialization.SharedModelDefaults;
import org.gradle.api.initialization.internal.SharedModelDefaultsInternal;
import org.gradle.internal.configuration.problems.IsolatedProjectsProblemsReporter;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.metaobject.MethodAccess;
import org.gradle.internal.metaobject.MethodMixIn;
import org.gradle.util.Path;

/**
 * The project-scoped view of the build-scoped {@link SharedModelDefaults} service, used when
 * Isolated Projects is enabled.
 *
 * <p>Shared model defaults can only be registered in settings scripts, inside the {@code defaults} block.
 * By the time a project is configured, the registrations have already been processed, and under Isolated
 * Projects the service is additionally a shared mutable resource accessed from parallel project
 * configuration. This view reports an Isolated Projects problem on registration attempts, so that the
 * violation is surfaced with proper location information instead of only the late
 * {@code IllegalStateException} of the underlying service. The registration is skipped, as it could
 * never take effect anyway.
 */
public class ProblemReportingSharedModelDefaults implements SharedModelDefaultsInternal, MethodMixIn {

    private final SharedModelDefaultsInternal delegate;
    private final IsolatedProjectsProblemsReporter problems;
    private final Path projectIdentityPath;

    public ProblemReportingSharedModelDefaults(
        SharedModelDefaults delegate,
        IsolatedProjectsProblemsReporter problems,
        Path projectIdentityPath
    ) {
        this.delegate = (SharedModelDefaultsInternal) delegate;
        this.problems = problems;
        this.projectIdentityPath = projectIdentityPath;
    }

    @Override
    public ProjectLayout getLayout() {
        return delegate.getLayout();
    }

    @Override
    public void withProjectLayout(ProjectLayout projectLayout, Runnable action) {
        delegate.withProjectLayout(projectLayout, action);
    }

    @Override
    public void processRegistrations() {
        delegate.processRegistrations();
    }

    @Override
    public <T> void add(String name, Class<T> publicType, Action<? super T> configureAction) {
        // The reporter throws in fail-fast mode. In diagnostics mode the problem is recorded and the
        // registration is skipped: it could never take effect, as registrations have already been processed.
        reportProjectScopeViolation();
    }

    @Override
    public MethodAccess getAdditionalMethods() {
        if (delegate instanceof MethodMixIn) {
            return new ProblemReportingMethodAccess(((MethodMixIn) delegate).getAdditionalMethods());
        }
        return NO_METHODS;
    }

    private static final MethodAccess NO_METHODS = new MethodAccess() {
        @Override
        public boolean hasMethod(String name, Object... arguments) {
            return false;
        }

        @Override
        public DynamicInvokeResult tryInvokeMethod(String name, Object... arguments) {
            return DynamicInvokeResult.notFound();
        }
    };

    private void reportProjectScopeViolation() {
        problems.report(factory ->
            factory.problem(null, messageBuilder -> {
                messageBuilder.text(
                    "Project '" + projectIdentityPath + "' cannot register shared model defaults. " +
                        "Shared model defaults can only be registered in a settings script, using the 'defaults' block."
                );
                return Unit.INSTANCE;
            }).exception().build()
        );
    }

    private class ProblemReportingMethodAccess implements MethodAccess {
        private final MethodAccess delegate;

        ProblemReportingMethodAccess(MethodAccess delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasMethod(String name, Object... arguments) {
            return delegate.hasMethod(name, arguments);
        }

        @Override
        public DynamicInvokeResult tryInvokeMethod(String name, Object... arguments) {
            if (delegate.hasMethod(name, arguments)) {
                reportProjectScopeViolation();
                return DynamicInvokeResult.found();
            }
            return delegate.tryInvokeMethod(name, arguments);
        }
    }
}
