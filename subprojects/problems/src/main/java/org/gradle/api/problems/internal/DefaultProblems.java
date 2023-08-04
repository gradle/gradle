/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.Problems;
import org.gradle.api.problems.interfaces.Problem;
import org.gradle.api.problems.interfaces.ProblemBuilderDefiningDocumentation;
import org.gradle.api.problems.interfaces.ProblemGroup;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.gradle.api.problems.interfaces.ProblemGroup.DEPRECATION_ID;
import static org.gradle.api.problems.interfaces.ProblemGroup.GENERIC_ID;
import static org.gradle.api.problems.interfaces.ProblemGroup.TYPE_VALIDATION_ID;
import static org.gradle.api.problems.interfaces.ProblemGroup.VERSION_CATALOG_ID;
import static org.gradle.api.problems.interfaces.Severity.ERROR;

public class DefaultProblems extends Problems {
    private final BuildOperationProgressEventEmitter buildOperationProgressEventEmitter;

    private final Map<String, ProblemGroup> problemGroups = new LinkedHashMap<>();

    public DefaultProblems(BuildOperationProgressEventEmitter buildOperationProgressEventEmitter) {
        this.buildOperationProgressEventEmitter = buildOperationProgressEventEmitter;
        addPredfinedGroup(GENERIC_ID);
        addPredfinedGroup(TYPE_VALIDATION_ID);
        addPredfinedGroup(DEPRECATION_ID);
        addPredfinedGroup(VERSION_CATALOG_ID);
    }

    private void addPredfinedGroup(String genericId) {
        problemGroups.put(genericId, new PredefinedProblemGroup(genericId));
    }

    public ProblemBuilderDefiningDocumentation createProblemBuilder() {
        return new DefaultProblemBuilder(this, buildOperationProgressEventEmitter);
    }


    public void collectError(Throwable failure) {
        new DefaultProblemBuilder(this, buildOperationProgressEventEmitter)
            .undocumented()
            .noLocation()
            .severity(ERROR)
            .message(failure.getMessage())
            .type("generic_exception")
            .group(GENERIC_ID)
            .cause(failure)
            .report();
    }

    @Override
    public void collectError(Problem problem) {
        buildOperationProgressEventEmitter.emitNowIfCurrent(problem);
//        ProblemsProgressEventEmitterHolder.get().emitNowIfCurrent(problem);
    }

    @Override
    public void collectErrors(Collection<Problem> problem) {
        problem.forEach(this::collectError);
    }

    @Override
    public ProblemGroup getProblemGroup(String groupId) {
        return problemGroups.get(groupId);
    }

    @Override
    public ProblemGroup registerProblemGroup(String typeId) {
        PredefinedProblemGroup value = new PredefinedProblemGroup(typeId);
        problemGroups.put(typeId, value);
        return value;
    }

    @Override
    public ProblemGroup registerProblemGroup(ProblemGroup typeId) {
        problemGroups.put(typeId.getId(), typeId);
        return typeId;
    }
}
