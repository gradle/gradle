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

package org.gradle.composite.internal

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildToolingModelAction
import org.gradle.internal.build.BuildToolingModelController
import org.gradle.internal.buildtree.BuildTreeFinishExecutor
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.buildtree.BuildTreeLifecycleControllerFactory
import org.gradle.internal.buildtree.BuildTreeWorkExecutor
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.tooling.provider.model.UnknownModelException
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderLookup

import java.util.function.Consumer
import java.util.function.Function

class TestBuildTreeLifecycleControllerFactory implements BuildTreeLifecycleControllerFactory {
    private final BuildTreeWorkGraph workGraph

    TestBuildTreeLifecycleControllerFactory(BuildTreeWorkGraph workGraph) {
        this.workGraph = workGraph
    }

    @Override
    BuildTreeLifecycleController createController(BuildLifecycleController targetBuild, BuildTreeWorkExecutor workExecutor, BuildTreeFinishExecutor finishExecutor) {
        return new TestBuildTreeLifecycleController(targetBuild, workExecutor, finishExecutor)
    }

    class TestBuildModelController implements BuildToolingModelController {
        private final BuildLifecycleController targetBuild

        TestBuildModelController(BuildLifecycleController targetBuild) {
            this.targetBuild = targetBuild
        }

        @Override
        GradleInternal getConfiguredModel() {
            return targetBuild.configuredBuild
        }

        @Override
        ToolingModelBuilderLookup.Builder locateBuilderForDefaultTarget(String modelName, boolean param) throws UnknownModelException {
            throw new UnsupportedOperationException()
        }

        @Override
        ToolingModelBuilderLookup.Builder locateBuilderForTarget(BuildState target, String modelName, boolean param) throws UnknownModelException {
            throw new UnsupportedOperationException()
        }

        @Override
        ToolingModelBuilderLookup.Builder locateBuilderForTarget(ProjectState target, String modelName, boolean param) throws UnknownModelException {
            throw new UnsupportedOperationException()
        }

        @Override
        boolean queryModelActionsRunInParallel() {
            return false
        }

        @Override
        void runQueryModelActions(Collection<? extends RunnableBuildOperation> actions) {
            throw new UnsupportedOperationException()
        }
    }

    class TestBuildTreeLifecycleController implements BuildTreeLifecycleController {
        private final BuildTreeFinishExecutor buildTreeFinishExecutor
        private final BuildLifecycleController targetBuild
        private final BuildTreeWorkExecutor workExecutor

        TestBuildTreeLifecycleController(BuildLifecycleController targetBuild, BuildTreeWorkExecutor workExecutor, BuildTreeFinishExecutor buildTreeFinishExecutor) {
            this.workExecutor = workExecutor
            this.targetBuild = targetBuild
            this.buildTreeFinishExecutor = buildTreeFinishExecutor
        }

        @Override
        void beforeBuild(Consumer<? super GradleInternal> action) {
            throw new UnsupportedOperationException()
        }

        @Override
        def <T> T withEmptyBuild(Function<? super SettingsInternal, T> action) {
            throw new UnsupportedOperationException()
        }

        @Override
        void scheduleAndRunTasks() {
            def plan = targetBuild.prepareToScheduleTasks()
            targetBuild.populateWorkGraph(plan) {
                it.addRequestedTasks()
            }
            def result = workExecutor.execute(workGraph)
            buildTreeFinishExecutor.finishBuildTree(result.failures)
            result.rethrow()
        }

        @Override
        def <T> T fromBuildModel(boolean runTasks, BuildToolingModelAction<? extends T> action) {
            def failures = []
            T result = null
            try {
                result = action.fromBuildModel(new TestBuildModelController(targetBuild))
            } catch (Throwable t) {
                failures.add(t)
            }
            buildTreeFinishExecutor.finishBuildTree(failures)
            return result
        }
    }
}
