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

package org.gradle.tooling.events.problems.internal;

import org.gradle.tooling.Failure;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.internal.BaseProgressEvent;
import org.gradle.tooling.events.problems.ProblemDescription;
import org.gradle.tooling.events.problems.ProblemExceptionMappingEvent;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public class DefaultProblemExceptionMappingEvent extends BaseProgressEvent implements ProblemExceptionMappingEvent {
    private final Map<Failure, Collection<ProblemDescription>> problemsForFailures;

    public DefaultProblemExceptionMappingEvent(
        long eventTime,
        OperationDescriptor descriptor,
        Map<Failure, Collection<ProblemDescription>> problemsForFailures
    ) {
        super(eventTime, "problem exception mapping", descriptor);
        this.problemsForFailures = problemsForFailures;
    }

    @Override
    public Map<Failure, Collection<ProblemDescription>> getProblemsForFailures() {
        return problemsForFailures;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultProblemExceptionMappingEvent that = (DefaultProblemExceptionMappingEvent) o;
        return Objects.equals(problemsForFailures, that.problemsForFailures);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(problemsForFailures);
    }
}
