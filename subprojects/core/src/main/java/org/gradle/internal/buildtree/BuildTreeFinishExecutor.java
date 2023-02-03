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

import javax.annotation.Nullable;
import java.util.List;

public interface BuildTreeFinishExecutor {
    /**
     * Finishes any work and runs any pending user clean up code such as build finished hooks, build service cleanup and so on.
     *
     * @param failures The failures to report to the build finished hooks.
     * @return The exception to throw, packages up the given failures plus any failures finishing the build.
     */
    @Nullable
    RuntimeException finishBuildTree(List<Throwable> failures);
}
