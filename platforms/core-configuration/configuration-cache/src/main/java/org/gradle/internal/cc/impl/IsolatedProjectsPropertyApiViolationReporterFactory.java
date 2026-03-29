/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.impl;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.PropertyApiViolationReporterFactory;
import org.gradle.internal.configuration.problems.ProblemFactory;
import org.gradle.internal.configuration.problems.ProblemsListener;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * {@link PropertyApiViolationReporterFactory} for Isolated Projects mode.
 *
 * <p>Creates per-project reporters that record deferred violations when
 * {@code Project.findProperty}, {@code Project.property}, {@code Project.hasProperty},
 * or {@code Project.getProperties} is called from a build script.</p>
 *
 * <p>The reporter wraps {@link IsolatedProjectsPropertyApiViolationReporter} (Kotlin) inside
 * a Java {@link java.util.function.Consumer} to avoid Kotlin class loading during
 * script initialization, which would interfere with
 * {@link org.gradle.internal.cc.impl.DefaultDynamicCallProblemReporting}.</p>
 */
@NullMarked
class IsolatedProjectsPropertyApiViolationReporterFactory implements PropertyApiViolationReporterFactory {

    private final ProblemsListener problemsListener;
    private final ProblemFactory problemFactory;

    IsolatedProjectsPropertyApiViolationReporterFactory(
        ProblemsListener problemsListener,
        ProblemFactory problemFactory
    ) {
        this.problemsListener = problemsListener;
        this.problemFactory = problemFactory;
    }

    @Override
    public @Nullable Consumer<String> createViolationReporter(ProjectInternal project) {
        // Defer Kotlin class loading: create reporter lazily on first violation,
        // not during script init (which would interfere with DynamicCallProblemReporting).
        return new Consumer<>() {
            private final IsolatedProjectsPropertyApiViolationReporter reporter = new IsolatedProjectsPropertyApiViolationReporter(project, problemsListener, problemFactory);
            @Override
            public void accept(String methodName) {
                reporter.reportViolation(methodName);
            }
        };
    }

    @Override
    public boolean hasReporter() {
        return true;
    }
}
