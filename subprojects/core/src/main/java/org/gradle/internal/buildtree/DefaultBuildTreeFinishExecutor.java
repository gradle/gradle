/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.build.NestedBuildState;

import java.util.ArrayList;
import java.util.List;

public class DefaultBuildTreeFinishExecutor implements BuildTreeFinishExecutor {
    private final BuildStateRegistry buildStateRegistry;
    private final ExceptionAnalyser exceptionAnalyser;
    private final BuildLifecycleController buildLifecycleController;

    public DefaultBuildTreeFinishExecutor(BuildStateRegistry buildStateRegistry,
                                          ExceptionAnalyser exceptionAnalyser,
                                          BuildLifecycleController buildLifecycleController) {
        this.buildStateRegistry = buildStateRegistry;
        this.exceptionAnalyser = exceptionAnalyser;
        this.buildLifecycleController = buildLifecycleController;
    }

    @Override
    public ExecutionResult<Void> finishBuildTree(List<Throwable> failures) {
        List<Throwable> finishFailures = new ArrayList<>();
        buildStateRegistry.visitBuilds(buildState -> {
            if (buildState instanceof NestedBuildState) {
                ExecutionResult<Void> result = ((NestedBuildState) buildState).finishBuild();
                finishFailures.addAll(result.getFailures());
            }
        });
        List<Throwable> allFailures = new ArrayList<>(failures);
        allFailures.addAll(finishFailures);
        RuntimeException reportableFailure = exceptionAnalyser.transform(allFailures);
        ExecutionResult<Void> result = buildLifecycleController.finishBuild(reportableFailure);
        finishFailures.addAll(result.getFailures());
        return ExecutionResult.maybeFailed(finishFailures);
    }
}
