/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.internal.tasks.testing.operations.ExecuteTestBuildOperationType;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;

/**
 * Build operation listener that filters test related build operations before forwarding to a delegate.
 *
 * @since 4.4
 */
class TestIgnoringBuildOperationListener implements BuildOperationListener {
    private final BuildOperationListener delegate;

    public TestIgnoringBuildOperationListener(BuildOperationListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        if (!(buildOperation.getDetails() instanceof ExecuteTestBuildOperationType.Details)) {
            delegate.started(buildOperation, startEvent);
        }
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        if (!(buildOperation.getDetails() instanceof ExecuteTestBuildOperationType.Details)) {
            delegate.finished(buildOperation, finishEvent);
        }
    }

}
