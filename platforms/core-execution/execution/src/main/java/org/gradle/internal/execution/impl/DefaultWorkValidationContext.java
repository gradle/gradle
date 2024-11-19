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

package org.gradle.internal.execution.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.internal.ProblemsProgressEventEmitterHolder;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.reflect.DefaultTypeValidationContext;
import org.gradle.internal.reflect.ProblemRecordingTypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.plugin.use.PluginId;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public class DefaultWorkValidationContext implements WorkValidationContext {
    private final Set<Class<?>> types = new HashSet<>();
    private final ImmutableList.Builder<Problem> problems = ImmutableList.builder();
    private final TypeOriginInspector typeOriginInspector;

    public DefaultWorkValidationContext(TypeOriginInspector typeOriginInspector) {
        this.typeOriginInspector = typeOriginInspector;
    }

    @Override
    public InternalProblems getProblemsService() {
        return ProblemsProgressEventEmitterHolder.get();
    }

    @Override
    public TypeValidationContext forType(Class<?> type, boolean cacheable) {
        types.add(type);
        Supplier<Optional<PluginId>> pluginId = () -> typeOriginInspector.findPluginDefining(type);
        return new ProblemRecordingTypeValidationContext(type, pluginId, getProblemsService()) {
            @Override
            protected void recordProblem(Problem problem) {
                if (DefaultTypeValidationContext.onlyAffectsCacheableWork(problem.getDefinition().getId()) && !cacheable) {
                    return;
                }
                problems.add(problem);
            }
        };
    }

    @Override
    public List<Problem> getProblems() {
        return problems.build();
    }

    @Override
    public ImmutableSortedSet<Class<?>> getValidatedTypes() {
        return ImmutableSortedSet.copyOf(Comparator.comparing(Class::getName), types);
    }
}
