/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.execution.steps;

import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.OutputSnapshotter;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.operations.BuildOperationExecutor;

public class StepFactory {
    /**
     * Ensures the step handles outputs correctly.
     *
     * This makes sure that
     * - notifying the caches about changing outputs,
     * - ensuring the output directories exist,
     * - and capturing the outputs after execution
     * happens in the right order.
     *
     * The delegate step is supposed to handle actually running the action which changes/creates the outputs.
     */
    public static <C extends BeforeExecutionContext> Step<C, AfterExecutionResult> prepareAndCaptureOutputs(
        BuildOperationExecutor buildOperationExecutor,
        UniqueId buildInvocationScopeId,
        OutputSnapshotter outputSnapshotter,
        OutputChangeListener outputChangeListener,
        Step<? super C, ? extends Result> delegate
    ) {
        return
            // @formatter:off
            new CaptureStateAfterExecutionStep<>(buildOperationExecutor, buildInvocationScopeId, outputSnapshotter,
            new BroadcastChangingOutputsStep<>(outputChangeListener,
            new CreateOutputsStep<>(delegate)));
            // @formatter:on
    }
}
