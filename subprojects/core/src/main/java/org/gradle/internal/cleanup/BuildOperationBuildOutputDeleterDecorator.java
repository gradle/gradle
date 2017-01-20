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

import org.gradle.api.Action;
import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.progress.BuildOperationDetails;
import org.gradle.internal.progress.BuildOperationExecutor;

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

    private BuildOperationDetails getDisplayName() {
        return BuildOperationDetails.displayName("Cleaning stale outputs for " + gradle.getIdentityPath().getName()).build();
    }

    @Override
    public void delete(final Iterable<File> outputs) {
        buildOperationExecutor.run(getDisplayName(), new Action<BuildOperationContext>() {
            @Override
            public void execute(BuildOperationContext buildOperationContext) {
                delegate.delete(outputs);
            }
        });
    }
}
