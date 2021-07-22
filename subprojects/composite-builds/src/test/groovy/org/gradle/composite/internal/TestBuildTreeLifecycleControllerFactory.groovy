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
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.buildtree.BuildTreeFinishExecutor
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.buildtree.BuildTreeLifecycleControllerFactory
import org.gradle.internal.buildtree.BuildTreeWorkExecutor

import java.util.function.Function

class TestBuildTreeLifecycleControllerFactory implements BuildTreeLifecycleControllerFactory {
    @Override
    BuildTreeLifecycleController createController(BuildLifecycleController targetBuild, BuildTreeWorkExecutor workExecutor, BuildTreeFinishExecutor finishExecutor) {
        return new TestBuildTreeLifecycleController(targetBuild, workExecutor, finishExecutor)
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
        GradleInternal getGradle() {
            throw new UnsupportedOperationException()
        }

        @Override
        def <T> T withEmptyBuild(Function<? super SettingsInternal, T> action) {
            throw new UnsupportedOperationException()
        }

        @Override
        void scheduleAndRunTasks() {
            targetBuild.prepareToScheduleTasks()
            targetBuild.scheduleRequestedTasks()
            def result = workExecutor.execute()
            buildTreeFinishExecutor.finishBuildTree(result.failures)
            result.rethrow()
        }

        @Override
        <T> T fromBuildModel(boolean runTasks, Function<? super GradleInternal, T> action) {
            def failures = []
            T result = null
            try {
                result = action.apply(targetBuild.configuredBuild)
            } catch (Throwable t) {
                failures.add(t)
            }
            buildTreeFinishExecutor.finishBuildTree(failures)
            return result
        }
    }
}
