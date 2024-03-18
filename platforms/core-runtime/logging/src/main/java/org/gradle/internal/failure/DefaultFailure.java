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

package org.gradle.internal.failure;

import com.google.common.collect.ImmutableList;

import java.util.List;

public class DefaultFailure implements Failure {

    private final Throwable original;
    private final List<StackTraceElement> stackTrace;
    private final List<StackTraceRelevance> frameRelevance;

    public DefaultFailure(Throwable original, List<StackTraceElement> stackTrace, List<StackTraceRelevance> frameRelevance) {
        this.original = original;
        this.stackTrace = stackTrace;
        this.frameRelevance = frameRelevance;
    }

    @Override
    public Throwable getOriginal() {
        return original;
    }

    @Override
    public String getMessage() {
        return original.getMessage();
    }

    @Override
    public String getHeader() {
        return original.toString();
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
    public List<Failure> getCauses() {
        return ImmutableList.of();
    }

    @Override
    public List<Failure> getSuppressed() {
        return ImmutableList.of();
    }
}
