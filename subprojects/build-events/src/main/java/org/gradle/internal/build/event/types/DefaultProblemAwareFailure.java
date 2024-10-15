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

import org.gradle.api.NonNullApi;
import org.gradle.api.problems.internal.Problem;
import org.gradle.api.problems.internal.ProblemAwareFailure;
import org.gradle.internal.exceptions.MultiCauseException;
import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion3;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.problem.InternalProblemAwareFailure;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@NonNullApi
public class DefaultProblemAwareFailure implements Serializable, InternalProblemAwareFailure {

    private final String message;
    private final String description;
    private final List<InternalFailure> causes;
    private final List<InternalBasicProblemDetailsVersion3> problems;

    private DefaultProblemAwareFailure(String message, String description, List<InternalFailure> causes, List<InternalBasicProblemDetailsVersion3> problems) {
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

    public static InternalFailure fromThrowable(Throwable t, Map<Throwable, Collection<Problem>> problemsMapping, Function<Problem, InternalBasicProblemDetailsVersion3> mapper) {
        StringWriter out = new StringWriter();
        PrintWriter wrt = new PrintWriter(out);
        t.printStackTrace(wrt);
        Throwable cause = t.getCause();
        List<InternalFailure> causeFailures;
        if (cause == null) {
            causeFailures = Collections.emptyList();
        } else if (cause instanceof MultiCauseException) {
            MultiCauseException multiCause = (MultiCauseException) cause;
            causeFailures = multiCause.getCauses().stream().map(f -> fromThrowable(f, problemsMapping, mapper)).collect(Collectors.toList());
        } else {
            causeFailures = Collections.singletonList(fromThrowable(cause, problemsMapping, mapper));
        }
        Collection<Problem> problemMapping = problemsMapping.get(t);
        List<Problem> problems = new ArrayList<>();
        if (problemMapping != null) {
            problems.addAll(problemMapping);
        }
        if (t instanceof ProblemAwareFailure) {
            problems.addAll(((ProblemAwareFailure) t).getProblems());
        }
        if (problems.isEmpty()) {
            return new DefaultFailure(t.getMessage(), out.toString(), causeFailures);
        } else {
            return new DefaultProblemAwareFailure(t.getMessage(), out.toString(), causeFailures, problems.stream().map(mapper).collect(Collectors.toList()));
        }
    }

    @Override
    public List<InternalBasicProblemDetailsVersion3> getProblems() {
        return problems;
    }
}
