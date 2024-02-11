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
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import javax.annotation.Nullable;

public class CaptureNonIncrementalStateBeforeExecutionStep<C extends PreviousExecutionContext, R extends CachingResult> extends AbstractCaptureStateBeforeExecutionStep<C, R> {

    public CaptureNonIncrementalStateBeforeExecutionStep(
        BuildOperationExecutor buildOperationExecutor,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        Step<? super BeforeExecutionContext, ? extends R> delegate
    ) {
        super(buildOperationExecutor, classLoaderHierarchyHasher, delegate);
    }

    @Override
    protected ImmutableSortedMap<String, FileSystemSnapshot> captureOutputSnapshots(UnitOfWork work, WorkspaceContext context) {
        ImmutableSortedMap.Builder<String, FileSystemSnapshot> builder = ImmutableSortedMap.naturalOrder();
        work.visitOutputs(context.getWorkspace(), new UnitOfWork.OutputVisitor() {
            @Override
            public void visitOutputProperty(String propertyName, TreeType type, UnitOfWork.OutputFileValueSupplier value) {
                builder.put(propertyName, FileSystemSnapshot.EMPTY);
            }
        });
        return builder.build();
    }

    @Nullable
    @Override
    protected OverlappingOutputs detectOverlappingOutputs(UnitOfWork work, PreviousExecutionContext context, ImmutableSortedMap<String, FileSystemSnapshot> unfilteredOutputSnapshots) {
        return null;
    }
}
