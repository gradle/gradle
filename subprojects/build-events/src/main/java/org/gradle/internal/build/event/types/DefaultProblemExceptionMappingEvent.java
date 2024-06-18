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

package org.gradle.internal.build.event.types;

import org.gradle.api.NonNullApi;
import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion3;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.InternalProblemExceptionMappingEvent;
import org.gradle.tooling.internal.protocol.events.InternalProblemDescriptor;

import java.util.Collection;
import java.util.Map;

@NonNullApi
public class DefaultProblemExceptionMappingEvent extends AbstractProgressEvent<InternalProblemDescriptor> implements InternalProblemExceptionMappingEvent {
    private final Map<InternalFailure, Collection<InternalBasicProblemDetailsVersion3>> problemsForFailures;

    public DefaultProblemExceptionMappingEvent(InternalProblemDescriptor problemDescriptor, Map<InternalFailure, Collection<InternalBasicProblemDetailsVersion3>> problemsForFailures) {
        super(System.currentTimeMillis(), problemDescriptor);
        this.problemsForFailures = problemsForFailures;
    }

    @Override
    public String getDisplayName() {
        return "problem exception mapping";
    }

    @Override
    public Map<InternalFailure, Collection<InternalBasicProblemDetailsVersion3>> getProblemsForFailures() {
        return problemsForFailures;
    }
}
