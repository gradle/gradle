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

package org.gradle.api.internal.tasks.compile.incremental.processing;

import java.io.Serializable;
import java.util.Set;

public class AnnotationProcessorResult implements Serializable {

    private final AnnotationProcessingResult processingResult;
    private final String className;
    private IncrementalAnnotationProcessorType type;
    private long executionTimeInMillis;

    public AnnotationProcessorResult(AnnotationProcessingResult processingResult, String className) {
        this.processingResult = processingResult;
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    public IncrementalAnnotationProcessorType getType() {
        return type;
    }

    public void setType(IncrementalAnnotationProcessorType type) {
        this.type = type;
    }

    public long getExecutionTimeInMillis() {
        return executionTimeInMillis;
    }

    public void setExecutionTimeInMillis(long executionTimeInMillis) {
        this.executionTimeInMillis = executionTimeInMillis;
    }

    public void addGeneratedType(String name, Set<String> originatingElements) {
        processingResult.addGeneratedType(name, originatingElements);
    }

    public void addGeneratedResource(GeneratedResource resource, Set<String> originatingElements) {
        processingResult.addGeneratedResource(resource, originatingElements);
    }

    public Set<String> getAggregatedTypes() {
        return processingResult.getAggregatedTypes();
    }

    public Set<String> getGeneratedAggregatingTypes() {
        return processingResult.getGeneratedAggregatingTypes();
    }

    public Set<GeneratedResource> getGeneratedAggregatingResources() {
        return processingResult.getGeneratedAggregatingResources();
    }

    public void setFullRebuildCause(String fullRebuildCause) {
        processingResult.setFullRebuildCause(fullRebuildCause);
    }
}
