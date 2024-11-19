/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.reflect;

import org.gradle.api.Action;
import org.gradle.api.problems.internal.AdditionalDataBuilderFactory;
import org.gradle.api.problems.internal.DefaultProblemBuilder;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.internal.TypeValidationDataSpec;
import org.gradle.internal.reflect.validation.DefaultTypeAwareProblemBuilder;
import org.gradle.internal.reflect.validation.TypeAwareProblemBuilder;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.plugin.use.PluginId;
import org.gradle.problems.buildtree.ProblemStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Supplier;

abstract public class ProblemRecordingTypeValidationContext implements TypeValidationContext {
    private final Class<?> rootType;
    private final Supplier<Optional<PluginId>> pluginId;
    private final InternalProblems problems;

    public ProblemRecordingTypeValidationContext(
        @Nullable Class<?> rootType,
        Supplier<Optional<PluginId>> pluginId,
        InternalProblems problems
    ) {
        this.rootType = rootType;
        this.pluginId = pluginId;
        this.problems = problems;
    }

    @Override
    public void visitTypeProblem(Action<? super TypeAwareProblemBuilder> problemSpec) {
        recordProblem(getDefaultTypeAwareProblemBuilder(problemSpec).build());
    }

    private Optional<PluginId> pluginId() {
        return pluginId.get();
    }

    @Override
    public void visitPropertyProblem(Action<? super TypeAwareProblemBuilder> problemSpec) {
        DefaultTypeAwareProblemBuilder problemBuilder = getDefaultTypeAwareProblemBuilder(problemSpec);
        problemBuilder.withAnnotationType(rootType);
        pluginId()
            .map(PluginId::getId)
            .ifPresent(id -> problemBuilder.additionalData(TypeValidationDataSpec.class, data -> data.pluginId(id)));
        recordProblem(problemBuilder.build());
    }

    private @Nonnull DefaultTypeAwareProblemBuilder getDefaultTypeAwareProblemBuilder(Action<? super TypeAwareProblemBuilder> problemSpec) {
        AdditionalDataBuilderFactory additionalDataBuilderFactory = problems.getAdditionalDataBuilderFactory();
        DefaultTypeAwareProblemBuilder problemBuilder = new DefaultTypeAwareProblemBuilder(new DefaultProblemBuilder((ProblemStream) null, additionalDataBuilderFactory), additionalDataBuilderFactory);
        problemSpec.execute(problemBuilder);
        return problemBuilder;
    }

    abstract protected void recordProblem(Problem problem);
}
