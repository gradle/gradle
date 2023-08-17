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

package org.gradle.api.problems;

import org.gradle.api.Incubating;
import org.gradle.api.problems.interfaces.Problem;
import org.gradle.api.problems.interfaces.ProblemBuilderDefiningLabel;
import org.gradle.api.problems.interfaces.ProblemGroup;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * No-op implementation of {@link Problems}.
 *
 * @since 8.4
 */
@Incubating
class NoOpProblems extends Problems {
    @Override
    public ProblemBuilderDefiningLabel createProblemBuilder() {
        return null;
    }

    @Override
    public void collectError(RuntimeException failure) {

    }

    @Override
    public void collectError(Problem problem) {

    }

    @Override
    public void collectErrors(Collection<Problem> problem) {

    }

    @Nullable
    @Override
    public ProblemGroup getProblemGroup(String groupId) {
        return null;
    }

    @Override
    public RuntimeException throwing(ProblemSpec action) {
        return null;
    }

    @Override
    public RuntimeException rethrowing(RuntimeException e, ProblemSpec action) {
        return null;
    }

    @Override
    public ProblemGroup registerProblemGroup(String typeId) {
        return null;
    }

    @Override
    public ProblemGroup registerProblemGroup(ProblemGroup typeId) {
        return null;
    }
}
