/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.execution;

import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.CurrentBuildOperationRef;

public class DefaultPostExecutionWorkQueue implements PostExecutionWorkQueue, Stoppable {

    private final ManagedExecutor executor;

    public DefaultPostExecutionWorkQueue(ManagedExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void submit(Runnable work) {
        BuildOperationRef capturedOperation = CurrentBuildOperationRef.instance().get();
        executor.execute(() -> CurrentBuildOperationRef.instance().with(capturedOperation, work));
    }

    @Override
    public void stop() {
        executor.stop();
    }
}
