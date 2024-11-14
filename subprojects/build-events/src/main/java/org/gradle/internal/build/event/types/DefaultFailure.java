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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.problems.internal.Problem;
import org.gradle.api.problems.internal.ProblemAwareFailure;
import org.gradle.internal.exceptions.MultiCauseException;
import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion3;
import org.gradle.tooling.internal.protocol.InternalFailure;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    public static InternalFailure fromThrowable(Throwable t) {
        return fromThrowable(t, Collections.emptyMap(), p -> null);
    }

    public static InternalFailure fromThrowable(Throwable t, Map<Throwable, Collection<Problem>> problemsMapping, Function<Problem, InternalBasicProblemDetailsVersion3> mapper) {
       return fromThrowable(t, problemsMapping, mapper, new ExceptionLookup(problemsMapping.keySet()));
    }

    private static InternalFailure fromThrowable(Throwable t, Map<Throwable, Collection<Problem>> problemsMapping, Function<Problem, InternalBasicProblemDetailsVersion3> mapper, ExceptionLookup exceptionLookup) {
        // Iterate through the cause hierarchy, with including multi-cause exceptions and convert them to a corresponding Failure with the same cause structure. If the current exception has a
        // corresponding problem in `problemsMapping` (ie the exception was thrown via ProblemReporter.throwing()), then the problem will be also available in the new failure object.
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

        Throwable throwable = exceptionLookup.find(t);
        Collection<Problem> problemMapping = throwable == null ? Collections.emptyList() : problemsMapping.get(throwable);

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
            return new DefaultFailure(t.getMessage(), out.toString(), causeFailures, problems.stream().map(mapper).collect(Collectors.toList()));
        }
    }

    /*
     * Workaround for the fact that the exception thrown by the worker is not the same instance as the one that was thrown by the build. With the lookup we can find the original exception by comparing
     * the stack frames. The comparison is expensive, so we only do when it's necessary (when the original exception does not contain the target and there's a matching class name and message).
     */
    private static class ExceptionLookup {
        private final Set<Throwable> exceptions;
        private final Multimap<String, Throwable> lookup;

        ExceptionLookup(Set<Throwable> exceptions) {
            this.exceptions = new HashSet<>(exceptions);
            this.lookup = initLookup(exceptions);
        }

        private static Multimap<String, Throwable> initLookup(Set<Throwable> exceptions) {
            Multimap<String, Throwable> lookup = ArrayListMultimap.create();
            for (Throwable exception : exceptions) {
                lookup.put(key(exception), exception);
            }
            return lookup;
        }

        private static String key(Throwable t) {
            return t.getClass().getName() + ":" + t.getMessage();
        }

        @Nullable
        public Throwable find(Throwable t) {
            if (exceptions.contains(t)) {
                return t;
            }
            Collection<Throwable> candidates = lookup.get(key(t));
            for (Throwable candidate : candidates) {
                if (deepEquals(candidate, t, new ArrayList<>())) {
                    return candidate;
                }
            }
            return null;
        }

        private static boolean deepEquals(Throwable t1, Throwable t2, List<Throwable> seen) {
            if (seen.contains(t1) || seen.contains(t2)) {
                return false; // drop self-references to avoid infinite recursion
            }

            if (t1 == null && t2 == null) {
                return true; // equals if both null
            } else if (t1 == null || t2 == null) {
                return false; // either t1 or t2 is null
            }

            if (!t1.getClass().equals(t2.getClass()) || !t1.getMessage().equals(t2.getMessage())) {
                return false;
            }
            StackTraceElement[] s1 = t1.getStackTrace();
            StackTraceElement[] s2 = t2.getStackTrace();
            for (int i = 0; i < s1.length && i < s2.length; i++) {
                if (!s1[i].getFileName().equals(s2[i].getFileName()) || s1[i].getLineNumber() != s2[i].getLineNumber()) {
                    return false;
                }
            }
            seen.add(t1);
            seen.add(t2);
            return deepEquals(t1.getCause(), t2.getCause(), seen);
        }
    }
}
