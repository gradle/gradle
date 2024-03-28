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

import java.util.List;

class DefaultFailure implements Failure {

    private final Class<? extends Throwable> exceptionType;
    private final String message;
    private final List<StackTraceElement> stackTrace;
    private final List<StackTraceRelevance> frameRelevance;
    private final List<Failure> suppressed;
    private final List<Failure> causes;

    public DefaultFailure(
        Throwable original,
        List<StackTraceElement> stackTrace,
        List<StackTraceRelevance> frameRelevance,
        List<Failure> suppressed,
        List<Failure> causes
    ) {
        if (stackTrace.size() != frameRelevance.size()) {
            throw new IllegalArgumentException("stackTrace and frameRelevance must have the same size.");
        }

        this.exceptionType = original.getClass();
        this.message = original.getLocalizedMessage(); // TODO: this can be null
        this.stackTrace = ImmutableList.copyOf(stackTrace);
        this.frameRelevance = ImmutableList.copyOf(frameRelevance);
        this.suppressed = ImmutableList.copyOf(suppressed);
        this.causes = ImmutableList.copyOf(causes);
    }

    @Override
    public Class<? extends Throwable> getExceptionType() {
        return exceptionType;
    }

    @Override
    public String getHeader() {
        return exceptionType.getName() + ": " + message;
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
}
