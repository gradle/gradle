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

package org.gradle.api.internal.project;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Factory for creating project-scoped violation reporters for the explicit
 * Project property API (findProperty, property, hasProperty, getProperties).
 *
 * <p>When Isolated Projects is enabled, the factory returns a reporter that
 * records a deferred violation. When disabled, it returns {@code null}.</p>
 */
@ServiceScope(Scope.Build.class)
public interface DynamicLookupRoutineFactory {
    /**
     * Creates a violation reporter for the given project, or {@code null} if
     * property API violations should not be reported.
     */
    @Nullable Consumer<String> createViolationReporter(ProjectInternal project);

    boolean hasReporter();
}
