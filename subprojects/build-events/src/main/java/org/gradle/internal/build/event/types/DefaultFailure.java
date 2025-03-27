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
package org.gradle.internal.build.event.types;

import com.google.common.collect.ImmutableList;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.api.problems.internal.ProblemLocator;
import org.gradle.internal.exceptions.MultiCauseException;
import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion3;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.jspecify.annotations.NonNull;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

public class DefaultFailure implements Serializable, InternalFailure {

    private final String message;
    private final String description;
    private final List<? extends InternalFailure> causes;
    private final List<InternalBasicProblemDetailsVersion3> problems;

    DefaultFailure(String message, String description, List<? extends InternalFailure> causes) {
        this(message, description, causes, Collections.emptyList());
    }

    DefaultFailure(String message, String description, List<? extends InternalFailure> causes, List<InternalBasicProblemDetailsVersion3> problems) {
        this.message = message;
        this.description = description;
        this.causes = causes;
        this.problems = problems;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public List<? extends InternalFailure> getCauses() {
        return causes;
    }

    @Override
    public List<InternalBasicProblemDetailsVersion3> getProblems() {
        return problems;
    }

    public static InternalFailure fromThrowable(Throwable throwable) {
        return fromThrowable(throwable, t -> ImmutableList.of(), p -> null);
    }

    public static InternalFailure fromThrowable(Throwable t, ProblemLocator problemLocator, Function<InternalProblem, InternalBasicProblemDetailsVersion3> mapper) {

        // Iterate through the cause hierarchy, with including multi-cause exceptions and convert them to a corresponding Failure with the same cause structure. If the current exception has a
        // corresponding problem in `problemsMapping` (ie the exception was thrown via ProblemReporter.throwing()), then the problem will be also available in the new failure object.
        StringWriter out = new StringWriter();
        PrintWriter wrt = new PrintWriter(out);
        t.printStackTrace(wrt);
        Throwable cause = t.getCause();
        List<InternalFailure> causeFailures = getCauseFailures(problemLocator, mapper, cause);
        List<InternalBasicProblemDetailsVersion3> problemMapping =
            problemLocator.findAll(t)
                .stream()
                .filter(Objects::nonNull)
                .map(mapper)
                .filter(Objects::nonNull)
                .collect(toList());

        return new DefaultFailure(t.getMessage(), out.toString(), causeFailures, problemMapping);
    }

    @NonNull
    private static List<InternalFailure> getCauseFailures(ProblemLocator problemLocator, Function<InternalProblem, InternalBasicProblemDetailsVersion3> mapper, Throwable cause) {
        if (cause == null) {
            return Collections.emptyList();
        } else if (cause instanceof MultiCauseException) {
            MultiCauseException multiCause = (MultiCauseException) cause;
            return multiCause.getCauses().stream().map(f -> fromThrowable(f, problemLocator, mapper)).collect(toList());
        } else {
            return Collections.singletonList(fromThrowable(cause, problemLocator, mapper));
        }
    }
}
