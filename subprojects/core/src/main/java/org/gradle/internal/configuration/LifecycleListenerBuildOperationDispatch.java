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

package org.gradle.internal.configuration;

import org.gradle.internal.configuration.LifecycleListenerExecutionBuildOperationType.DetailsImpl;
import org.gradle.internal.dispatch.Dispatch;
import org.gradle.internal.dispatch.FilteringDispatch;
import org.gradle.internal.dispatch.MethodInvocation;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;

public class LifecycleListenerBuildOperationDispatch implements Dispatch<MethodInvocation> {

    private final Dispatch<MethodInvocation> delegate;
    private final BuildOperationExecutor buildOperationExecutor;
    private final long parentBuildOperationId;

    public LifecycleListenerBuildOperationDispatch(Dispatch<MethodInvocation> delegate, BuildOperationExecutor buildOperationExecutor, long parentBuildOperationId) {
        this.delegate = delegate;
        this.buildOperationExecutor = buildOperationExecutor;
        this.parentBuildOperationId = parentBuildOperationId;
    }

    @Override
    public void dispatch(final MethodInvocation message) {
        // we have this check for FilteringDispatch here so that we don't emit ops for unimplemented
        // methods when we e.g. turn a closure into a dynamic proxy of an interface with a bunch of other methods
        if (!(delegate instanceof FilteringDispatch) || ((FilteringDispatch<MethodInvocation>) delegate).willDispatch(message)) {
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    delegate.dispatch(message);
                    context.setResult(LifecycleListenerExecutionBuildOperationType.RESULT);
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return new DetailsImpl(parentBuildOperationId).desc();
                }
            });
        }
    }
}
