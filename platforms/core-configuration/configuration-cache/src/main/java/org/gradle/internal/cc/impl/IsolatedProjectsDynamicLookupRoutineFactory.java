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

import org.gradle.api.internal.project.DynamicLookupRoutineFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.configuration.problems.ProblemFactory;
import org.gradle.internal.configuration.problems.ProblemsListener;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Java implementation of DynamicLookupRoutineFactory to avoid Kotlin lambda
 * class loading issues during script initialization.
 */
@NullMarked
class IsolatedProjectsDynamicLookupRoutineFactory implements DynamicLookupRoutineFactory {

    private final boolean isolatedProjects;
    private final @Nullable ProblemsListener problemsListener;
    private final @Nullable ProblemFactory problemFactory;

    IsolatedProjectsDynamicLookupRoutineFactory(
        boolean isolatedProjects,
        @Nullable ProblemsListener problemsListener,
        @Nullable ProblemFactory problemFactory
    ) {
        this.isolatedProjects = isolatedProjects;
        this.problemsListener = problemsListener;
        this.problemFactory = problemFactory;
    }

    @Override
    public @Nullable Consumer<String> createViolationReporter(ProjectInternal project) {
        if (!isolatedProjects) {
            return null;
        }
        // Defer Kotlin class loading: create reporter lazily on first violation,
        // not during script init (which would interfere with DynamicCallProblemReporting).
        return new Consumer<String>() {
            private IsolatedProjectsPropertyApiViolationReporter reporter;
            @Override
            public void accept(String methodName) {
                if (reporter == null) {
                    reporter = new IsolatedProjectsPropertyApiViolationReporter(project, problemsListener, problemFactory);
                }
                reporter.reportViolation(methodName);
            }
        };
    }
}
