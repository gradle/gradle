/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.reflect.validation;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.plugin.use.PluginId;
import org.gradle.problems.Solution;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

abstract class AbstractValidationProblemBuilder<T extends ValidationProblemBuilder<T>> implements ValidationProblemBuilder<T> {
    protected final DocumentationRegistry documentationRegistry;
    protected final PluginId pluginId;
    protected ValidationProblemId problemId = null;
    protected Severity severity = Severity.WARNING;
    protected Supplier<String> shortProblemDescription;
    protected Supplier<String> longDescription = () -> null;
    protected Supplier<String> reason = () -> null;
    protected UserManualReference userManualReference;
    protected final List<Supplier<Solution>> possibleSolutions = Lists.newArrayListWithExpectedSize(1);
    protected boolean cacheabilityProblemOnly = false;
    protected boolean typeIrrelevantInErrorMessage = false;

    public AbstractValidationProblemBuilder(DocumentationRegistry documentationRegistry, @Nullable PluginId pluginId) {
        this.documentationRegistry = documentationRegistry;
        this.pluginId = pluginId;
    }

    @Override
    public T withId(ValidationProblemId id) {
        this.problemId = id;
        return Cast.uncheckedCast(this);
    }

    @Override
    public T withDescription(Supplier<String> message) {
        this.shortProblemDescription = message;
        return Cast.uncheckedCast(this);
    }

    @Override
    public T reportAs(Severity severity) {
        this.severity = severity;
        return Cast.uncheckedCast(this);
    }

    @Override
    public T happensBecause(Supplier<String> message) {
        this.reason = message;
        return Cast.uncheckedCast(this);
    }

    @Override
    public T withLongDescription(Supplier<String> longDescription) {
        this.longDescription = longDescription;
        return Cast.uncheckedCast(this);
    }

    @Override
    public T documentedAt(String id, String section) {
        this.userManualReference = new UserManualReference(documentationRegistry, id, section);
        return Cast.uncheckedCast(this);
    }

    @Override
    public T addPossibleSolution(Supplier<String> solution, Action<? super SolutionBuilder> solutionSpec) {
        DefaultSolutionBuilder builder = new DefaultSolutionBuilder(documentationRegistry, solution);
        solutionSpec.execute(builder);
        possibleSolutions.add(builder.build());
        return Cast.uncheckedCast(this);
    }

    @Override
    public T onlyAffectsCacheableWork() {
        this.cacheabilityProblemOnly = true;
        return Cast.uncheckedCast(this);
    }

    @Override
    public T typeIsIrrelevantInErrorMessage() {
        this.typeIrrelevantInErrorMessage = true;
        return Cast.uncheckedCast(this);
    }

    public abstract TypeValidationProblem build();

}
