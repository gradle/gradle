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

import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.internal.exceptions.MultiCauseException;
import org.gradle.internal.problems.failure.DefaultFailureFactory;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.problems.failure.FailurePrinter;
import org.gradle.tooling.internal.protocol.FailureDescriptionReconstructor;
import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion3;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class DefaultFailure implements Serializable, InternalFailure {

    private final String message;
    private final @Nullable String fullDescription;
    private final String ownDescription;
    private final List<? extends InternalFailure> causes;
    private final List<InternalBasicProblemDetailsVersion3> problems;
    private transient @Nullable String reconstructedDescription;

    DefaultFailure(String message, String description, List<? extends InternalFailure> causes) {
        this(message, description, causes, Collections.emptyList());
    }

    DefaultFailure(String message, String description, List<? extends InternalFailure> causes, List<InternalBasicProblemDetailsVersion3> problems) {
        this(message, description, description, causes, problems);
    }

    private DefaultFailure(String message, @Nullable String fullDescription, String ownDescription, List<? extends InternalFailure> causes, List<InternalBasicProblemDetailsVersion3> problems) {
        this.message = message;
        this.fullDescription = fullDescription;
        this.ownDescription = ownDescription;
        this.causes = causes;
        this.problems = problems;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getDescription() {
        if (fullDescription != null) {
            return fullDescription;
        }
        String reconstructed = reconstructedDescription;
        if (reconstructed == null) {
            // Walk the concrete node tree, not the InternalFailure interface, so this stays callable when an older
            // Tooling API consumer (whose InternalFailure has no getOwnDescription) deserializes and reads this object.
            reconstructed = FailureDescriptionReconstructor.reconstruct(this, DefaultFailure::getOwnDescription, DefaultFailure::ownCauses);
            reconstructedDescription = reconstructed;
        }
        return reconstructed;
    }

    private List<DefaultFailure> ownCauses() {
        List<DefaultFailure> result = new ArrayList<>(causes.size());
        for (InternalFailure cause : causes) {
            result.add((DefaultFailure) cause);
        }
        return result;
    }

    @Override
    public List<? extends InternalFailure> getCauses() {
        return causes;
    }

    @Override
    public List<InternalBasicProblemDetailsVersion3> getProblems() {
        return problems;
    }

    @Override
    public String getOwnDescription() {
        return ownDescription;
    }

    public static InternalFailure fromThrowable(Throwable throwable) {
        return fromThrowable(throwable, p -> null);
    }

    public static InternalFailure fromThrowable(Throwable t, Function<ProblemInternal, InternalBasicProblemDetailsVersion3> mapper) {
        Failure failure = DefaultFailureFactory.withDefaultClassifier().create(t);
        return fromFailure(failure, mapper);
    }

    public static InternalFailure fromFailure(Failure buildFailure, Function<ProblemInternal, InternalBasicProblemDetailsVersion3> mapper) {
        return fromFailure(buildFailure, mapper, FailureCache.NONE);
    }

    public static InternalFailure fromFailure(Failure buildFailure, Function<ProblemInternal, InternalBasicProblemDetailsVersion3> mapper, FailureCache cache) {
        // Reuse the conversion of a throwable already seen, e.g. a configuration failure shared across per-project fetches.
        InternalFailure cached = cache.get(buildFailure.getOriginal());
        if (cached != null) {
            return cached;
        }
        // Build the failure tree in a single pass: each node carries only its own description. The full description (the
        // whole cause subtree) is reconstructed lazily from these on demand, so no node prints its subtree here. Children
        // are interned before the parent, so the cache is never re-entered while a single mapping is being computed.
        String ownDescription = FailurePrinter.printNodeToString(buildFailure);
        List<InternalFailure> causeFailures = convertCausesToFailures(buildFailure.getCauses(), mapper, cache);
        List<InternalBasicProblemDetailsVersion3> problemDetails = buildFailure.getProblems().stream()
            .map(mapper)
            .collect(toList());

        DefaultFailure converted = new DefaultFailure(buildFailure.getMessage(), null, ownDescription, causeFailures, problemDetails);
        return cache.intern(buildFailure.getOriginal(), converted);
    }

    private static List<InternalFailure> convertCausesToFailures(
        List<Failure> causes,
        Function<ProblemInternal, InternalBasicProblemDetailsVersion3> mapper,
        FailureCache cache
    ) {
        return causes.stream()
            // Multi cause exceptions are dropped from the cause structure (the reason predates this code); for example
            // TaskExecutionException is a MultiCauseException, so the failed task is not surfaced as context here.
            // getDescription reconstructs over this same structure, so it omits the dropped node too, unlike a direct
            // FailurePrinter.printToString of the source.
            .flatMap(cause -> cause.getOriginal() instanceof MultiCauseException
                ? cause.getCauses().stream()
                : Stream.of(cause))
            .map(cause -> fromFailure(cause, mapper, cache))
            .collect(toList());
    }


    @Override
    public String toString() {
        return "DefaultFailure{" +
            "message='" + message + '\'' +
            ", ownDescription='" + ownDescription + '\'' +
            ", causes=" + causes +
            ", problems=" + problems +
            '}';
    }
}
