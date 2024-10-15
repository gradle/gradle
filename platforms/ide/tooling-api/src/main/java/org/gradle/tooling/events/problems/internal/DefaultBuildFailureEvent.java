/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.gradle.tooling.Failure;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.internal.BaseProgressEvent;
import org.gradle.tooling.events.problems.BuildFailureEvent;

import javax.annotation.Nullable;
import java.util.List;

@NonNullApi
public class DefaultBuildFailureEvent extends BaseProgressEvent implements BuildFailureEvent {

    private final List<Failure> failures;

    public DefaultBuildFailureEvent(
        long eventTime,
        @Nullable OperationDescriptor problemDescriptor,
        List<Failure> failures) {
        super(eventTime, problemDescriptor == null ? "<null>" : problemDescriptor.getDisplayName(), problemDescriptor);
        this.failures = failures;
    }

    @Override
    public  List<Failure> getFailures() {
        return failures;
    }
}
