/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.tooling;

import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.events.InternalJavaCompileTaskSuccessResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskCachedResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskSuccessResult;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;

public class DefaultJavaCompileTaskSuccessResult implements InternalJavaCompileTaskSuccessResult, InternalTaskCachedResult, Serializable {
    private final InternalTaskSuccessResult delegate;
    private final List<InternalAnnotationProcessorResult> annotationProcessorResults;

    public DefaultJavaCompileTaskSuccessResult(InternalTaskSuccessResult delegate, List<InternalAnnotationProcessorResult> annotationProcessorResults) {
        this.delegate = delegate;
        this.annotationProcessorResults = annotationProcessorResults;
    }

    @Override
    public boolean isUpToDate() {
        return delegate.isUpToDate();
    }

    @Override
    public boolean isFromCache() {
        return (delegate instanceof InternalTaskCachedResult) && ((InternalTaskCachedResult) delegate).isFromCache();
    }

    @Override
    public long getStartTime() {
        return delegate.getStartTime();
    }

    @Override
    public long getEndTime() {
        return delegate.getEndTime();
    }

    @Override
    public String getOutcomeDescription() {
        return delegate.getOutcomeDescription();
    }

    @Override
    public List<? extends InternalFailure> getFailures() {
        return delegate.getFailures();
    }

    @Nullable
    @Override
    public List<InternalAnnotationProcessorResult> getAnnotationProcessorResults() {
        return annotationProcessorResults;
    }
}
