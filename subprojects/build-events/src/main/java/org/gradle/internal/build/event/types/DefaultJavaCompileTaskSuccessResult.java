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

import org.gradle.tooling.internal.protocol.events.InternalJavaCompileTaskOperationResult;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class DefaultJavaCompileTaskSuccessResult extends DefaultTaskSuccessResult implements InternalJavaCompileTaskOperationResult {

    private final List<InternalAnnotationProcessorResult> annotationProcessorResults;

    public DefaultJavaCompileTaskSuccessResult(DefaultTaskSuccessResult delegate, List<InternalAnnotationProcessorResult> annotationProcessorResults) {
        super(delegate.getStartTime(), delegate.getEndTime(), delegate.isUpToDate(), delegate.isFromCache(), delegate.getOutcomeDescription(), delegate.isIncremental(), delegate.getExecutionReasons());
        this.annotationProcessorResults = annotationProcessorResults;
    }

    @Nullable
    @Override
    public List<InternalAnnotationProcessorResult> getAnnotationProcessorResults() {
        return annotationProcessorResults;
    }
}
