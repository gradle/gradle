/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.problems.failure;

import com.google.common.collect.ImmutableList;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.internal.exceptions.CompilationFailedIndicator;

import java.util.Collections;
import java.util.List;

class DefaultFailure implements Failure {

    private final Throwable original;
    private final List<StackTraceElement> stackTrace;
    private final List<StackTraceRelevance> frameRelevance;
    private final List<Failure> suppressed;
    private final List<Failure> causes;
    private final List<InternalProblem> problems;

    public DefaultFailure(
        Throwable original,
        List<StackTraceElement> stackTrace,
        List<StackTraceRelevance> frameRelevance,
        List<Failure> suppressed,
        List<Failure> causes,
        List<InternalProblem> problems
    ) {
        if (stackTrace.size() != frameRelevance.size()) {
            throw new IllegalArgumentException("stackTrace and frameRelevance must have the same size.");
        }

        this.original = original;
        this.stackTrace = ImmutableList.copyOf(stackTrace);
        this.frameRelevance = ImmutableList.copyOf(frameRelevance);
        this.suppressed = ImmutableList.copyOf(suppressed);
        this.causes = ImmutableList.copyOf(causes);
        this.problems = ImmutableList.copyOf(problems);
    }

    @Override
    public Class<? extends Throwable> getExceptionType() {
        return original.getClass();
    }

    @Override
    public String getHeader() {
        return original.toString();
    }

    @Override
    public String getMessage() {
        // TODO: Remove this when we handle problems uniformly and don't do the compilation failure as a special case
        return (original instanceof CompilationFailedIndicator) ? ((CompilationFailedIndicator) original).getShortMessage() : original.getMessage();
    }

    @Override
    public List<StackTraceElement> getStackTrace() {
        return stackTrace;
    }

    @Override
    public StackTraceRelevance getStackTraceRelevance(int frameIndex) {
        return frameRelevance.get(frameIndex);
    }

    @Override
    public List<Failure> getSuppressed() {
        return suppressed;
    }

    @Override
    public List<Failure> getCauses() {
        return causes;
    }

    @Override
    public List<InternalProblem> getProblems() {
        return problems;
    }

    @Override
    public Throwable getOriginal() {
        return original;
    }

    @Override
    public int indexOfStackFrame(int fromIndex, StackFramePredicate predicate) {
        int size = stackTrace.size();
        for (int i = fromIndex; i < size; i++) {
            if (predicate.test(stackTrace.get(i), getStackTraceRelevance(i))) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public Failure withoutProblems() {
        return new DefaultFailure(
            original,
            stackTrace,
            frameRelevance,
            suppressed,
            causes,
            Collections.emptyList()
        );
    }

    @Override
    public String toString() {
        return "DefaultFailure{" +
            "original=" + original +
            ", stackTrace=" + stackTrace +
            ", frameRelevance=" + frameRelevance +
            ", suppressed=" + suppressed +
            ", causes=" + causes +
            ", problems=" + problems +
            '}';
    }
}
