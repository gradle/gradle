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

package org.gradle.tooling.internal.consumer;

import org.gradle.api.NonNullApi;
import org.gradle.tooling.Failure;
import org.gradle.tooling.events.FailureResult;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationResult;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@NonNullApi
public class BuildFailedProgressAdapter implements ProgressListener {

    public List<Failure> failures = new ArrayList<>();

    @Override
    public void statusChanged(ProgressEvent event) {
        if (isBuildFinishedEvent(event)) {
            OperationResult result = ((FinishEvent) event).getResult();
            if (result instanceof FailureResult) {
                this.failures = new ArrayList<>(((FailureResult) result).getFailures());
            } else {
                this.failures = Collections.emptyList();
            }
        }
    }

    private static boolean isBuildFinishedEvent(ProgressEvent event) {
        return event instanceof FinishEvent && event.getDescriptor().getParent() == null;
    }
}
