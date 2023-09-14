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
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.ReportableProblem;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.reflect.ProblemRecordingTypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.plugin.use.PluginId;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableList.builder;
import static org.gradle.internal.reflect.problems.ValidationProblemId.onlyAffectsCacheableWork;

public class DefaultWorkValidationContext implements WorkValidationContext {
    private final Set<Class<?>> types = new HashSet<>();
    private final ImmutableList.Builder<ReportableProblem> problems = builder();
    private final TypeOriginInspector typeOriginInspector;
    private final Problems problemsService;

    public DefaultWorkValidationContext(TypeOriginInspector typeOriginInspector, Problems problemsService) {
        this.problemsService = problemsService;
        this.typeOriginInspector = typeOriginInspector;
    }

    @Override
    public TypeValidationContext forType(Class<?> type, boolean cacheable) {
        types.add(type);
        Supplier<Optional<PluginId>> pluginId = () -> typeOriginInspector.findPluginDefining(type);
        return new ProblemRecordingTypeValidationContext(type, pluginId) {
            @Override
            protected void recordProblem(ReportableProblem problem) {
                if (onlyAffectsCacheableWork(problem.getProblemType()) && !cacheable) {
                    return;
                }
                problems.add(problem);
            }
        };
    }

    @Override
    public List<ReportableProblem> getProblems() {
        return problems.build();
    }

    public ImmutableSortedSet<Class<?>> getValidatedTypes() {
        return ImmutableSortedSet.copyOf(Comparator.comparing(Class::getName), types);
    }
}
