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
import org.gradle.api.problems.interfaces.ProblemBuilder;
import org.gradle.api.problems.interfaces.ProblemBuilderDefiningDocumentation;
import org.gradle.api.problems.interfaces.ProblemGroup;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

import static java.util.Collections.singleton;

/**
 * Problems API service.
 *
 * @since 8.4
 */
@Incubating
@ServiceScope(Scope.Global.class)
public abstract class Problems {

    /**
     * A function that can be used to specify a {@link Problem} using a {@link ProblemBuilder}.
     * <p>
     * Usage example:
     *
     * <pre>
     * throw getProblemService().throwing(builder -&gt;
     *        builder.undocumented()
     *            .location(...)
     *            .message(message)
     *            .type("task_selection")
     *            .group(ProblemGroup.GENERIC_ID)
     *            .severity(Severity.ERROR)
     *            .withException(new TaskSelectionException(message)));
     * </pre>
     *
     * Using this instead of an {@link org.gradle.api.Action} forces the user to specify all required properties of a {@link Problem}.
     *
     * @since 8.4
     */
    @Incubating
    public interface ProblemSpec {

        @Nonnull
        ProblemBuilder apply(ProblemBuilderDefiningDocumentation builder);
    }

    private static Problems problemsService = new NoOpProblems();

    abstract public ProblemBuilderDefiningDocumentation createProblemBuilder();

    abstract public void collectError(RuntimeException failure);


    abstract public void collectError(Problem problem);

    abstract public void collectErrors(Collection<Problem> problem);

    abstract public @Nullable ProblemGroup getProblemGroup(String groupId);

    abstract public RuntimeException throwing(ProblemSpec action);

    abstract public RuntimeException rethrowing(RuntimeException e, ProblemSpec action);

    abstract public ProblemGroup registerProblemGroup(String typeId);

    abstract public ProblemGroup registerProblemGroup(ProblemGroup typeId);

    protected static void collect(RuntimeException failure) {
        problemsService.collectError(failure);
    }

    public static void init(Problems problemsService) {
        Problems.problemsService = problemsService;
    }

    protected static void collect(Problem problem) {
        problemsService.collectError(problem);
    }

    protected static ProblemBuilderDefiningDocumentation create() {
        return problemsService.createProblemBuilder();
    }

    protected static RuntimeException throwing(ProblemBuilder problem, RuntimeException cause) {
        problem.cause(cause);
        return throwing(singleton(problem.build()), cause);
    }

    protected static RuntimeException throwing(Collection<Problem> problems, RuntimeException cause) {
        collect(problems);
        throw cause;
    }

    protected static void collect(Collection<Problem> problems) {
        problemsService.collectErrors(problems);
    }

}
