/*
 * Copyright 2011 the original author or authors.
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

import com.google.common.collect.Lists;
import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.build.ExecutionResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DefaultBuildWorkExecutor implements BuildWorkExecutor {
    private final List<BuildExecutionAction> executionActions;

    public DefaultBuildWorkExecutor(Iterable<? extends BuildExecutionAction> executionActions) {
        this.executionActions = Lists.newArrayList(executionActions);
    }

    @Override
    public ExecutionResult<Void> execute(GradleInternal gradle) {
        List<Throwable> failures = new ArrayList<>();
        execute(gradle, 0, failures);
        return ExecutionResult.maybeFailed(failures);
    }

    private void execute(final GradleInternal gradle, final int index, final Collection<? super Throwable> taskFailures) {
        if (index >= executionActions.size()) {
            return;
        }
        executionActions.get(index).execute(new BuildExecutionContext() {
            @Override
            public GradleInternal getGradle() {
                return gradle;
            }

            @Override
            public void proceed() {
                execute(gradle, index + 1, taskFailures);
            }

        }, taskFailures);
    }
}
