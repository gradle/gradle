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

package org.gradle.internal.execution;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.plugin.use.PluginId;

import java.util.List;
import java.util.Optional;

public interface WorkValidationContext {
    TypeValidationContext forType(Class<?> type, boolean cacheable);

    InternalProblems getProblemsService();

    List<InternalProblem> getProblems();

    ImmutableSet<Class<?>> getValidatedTypes();

    interface TypeOriginInspector {
        TypeOriginInspector NO_OP = type -> Optional.empty();

        Optional<PluginId> findPluginDefining(Class<?> type);
    }
}
