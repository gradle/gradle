/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.initialization;

import org.gradle.composite.internal.TaskIdentifier;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.StandAloneNestedBuild;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@ServiceScope(Scopes.BuildTree.class)
public interface BuildLogicBuildQueue {

    <T> T build(BuildState requester, List<TaskIdentifier.TaskBasedTaskIdentifier> tasks, Supplier<T> continuationUnderLock);

    <T> T buildBuildSrc(StandAloneNestedBuild buildSrcBuild, Function<BuildTreeLifecycleController, T> continuationUnderLock);
}

