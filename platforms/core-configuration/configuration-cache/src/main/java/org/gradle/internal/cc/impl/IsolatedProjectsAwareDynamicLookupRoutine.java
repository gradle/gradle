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

import org.gradle.api.internal.project.DelegatingDynamicLookupRoutine;
import org.gradle.api.internal.project.DynamicLookupRoutine;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.configuration.problems.ProblemFactory;
import org.gradle.internal.configuration.problems.ProblemsListener;
import org.gradle.internal.metaobject.DynamicObject;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A project-scoped {@link DynamicLookupRoutine} that reports Isolated Projects violations
 * when the explicit Project property API is accessed from a build script.
 *
 * <p>Extends {@link DelegatingDynamicLookupRoutine} (in core) to inherit the Java varargs
 * pass-through for {@code invokeMethod}, which is required by the dynamic call tracking
 * infrastructure.</p>
 */
class IsolatedProjectsAwareDynamicLookupRoutine extends DelegatingDynamicLookupRoutine {

    @SuppressWarnings("unused")
    private final IsolatedProjectsPropertyApiViolationReporter reporter;

    IsolatedProjectsAwareDynamicLookupRoutine(
        DynamicLookupRoutine delegate,
        ProjectInternal project,
        ProblemsListener problems,
        ProblemFactory problemFactory
    ) {
        super(delegate);
        this.reporter = new IsolatedProjectsPropertyApiViolationReporter(project, problems, problemFactory);
    }

    @Override
    public boolean hasProperty(DynamicObject receiver, String propertyName) {
        return super.hasProperty(receiver, propertyName);
    }

    @Override
    public @Nullable Map<String, ?> getProperties(DynamicObject receiver) {
        return super.getProperties(receiver);
    }

    @Override
    public @Nullable Object invokeMethod(DynamicObject receiver, String name, Object... args) {
        return super.invokeMethod(receiver, name, args);
    }
}
