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

package org.gradle.execution;

import org.gradle.api.internal.GradleInternal;
import org.gradle.composite.internal.IncludedBuildControllers;

import java.util.Collection;

public class IncludedBuildLifecycleBuildWorkExecutor implements BuildWorkExecutor {
    private final BuildWorkExecutor delegate;
    private final IncludedBuildControllers includedBuildControllers;

    public IncludedBuildLifecycleBuildWorkExecutor(BuildWorkExecutor delegate, IncludedBuildControllers includedBuildControllers) {
        this.delegate = delegate;
        this.includedBuildControllers = includedBuildControllers;
    }

    @Override
    public void execute(GradleInternal gradle, Collection<? super Throwable> failures) {
        includedBuildControllers.startTaskExecution();
        delegate.execute(gradle, failures);
        includedBuildControllers.awaitTaskCompletion(failures);
    }
}
