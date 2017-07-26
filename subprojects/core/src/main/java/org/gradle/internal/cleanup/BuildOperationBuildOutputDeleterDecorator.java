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

package org.gradle.internal.cleanup;

import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.util.GUtil;

import java.io.File;

public class BuildOperationBuildOutputDeleterDecorator implements BuildOutputDeleter {
    private final GradleInternal gradle;
    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildOutputDeleter delegate;

    public BuildOperationBuildOutputDeleterDecorator(GradleInternal gradle, BuildOperationExecutor buildOperationExecutor, BuildOutputDeleter delegate) {
        this.gradle = gradle;
        this.buildOperationExecutor = buildOperationExecutor;
        this.delegate = delegate;
    }

    @Override
    public void delete(final Iterable<File> outputs) {
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                delegate.delete(outputs);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                String rootProjectIdentifier = GUtil.elvis(gradle.getIdentityPath().getName(), "root build");
                return BuildOperationDescriptor.displayName("Clean stale outputs for " + rootProjectIdentifier).progressDisplayName("Cleaning stale outputs for " + rootProjectIdentifier);
            }
        });
    }
}
