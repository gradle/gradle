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

package org.gradle.internal.execution.steps;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.OutputVisitor;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.impl.DefaultBeforeExecutionState;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.snapshot.FileSystemSnapshot;

public class CaptureImmutableStateBeforeExecutionStep<C extends WorkspaceContext, R extends CachingResult> implements Step<C, R> {

    private final Step<? super ImmutableBeforeExecutionContext, ? extends R> delegate;

    public CaptureImmutableStateBeforeExecutionStep(
        Step<? super ImmutableBeforeExecutionContext, ? extends R> delegate
    ) {
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        ImmutableSortedMap.Builder<String, FileSystemSnapshot> outputProperties = ImmutableSortedMap.naturalOrder();
        work.visitOutputs(context.getWorkspace(), new OutputVisitor() {
            @Override
            public void visitOutputProperty(String propertyName, TreeType type, OutputFileValueSupplier value) {
                outputProperties.put(propertyName, FileSystemSnapshot.EMPTY);
            }
        });
        return delegate.execute(work, new ImmutableBeforeExecutionContext(
            context,
            new DefaultBeforeExecutionState(
                context.getImplementation(),
                context.getAdditionalImplementations(),
                context.getInputProperties(),
                context.getInputFileProperties(),
                outputProperties.build()
            )
        ));
    }
}
