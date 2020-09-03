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

package org.gradle.launcher.exec;

import org.gradle.api.internal.BuildType;
import org.gradle.internal.buildtree.BuildTreeState;
import org.gradle.internal.invocation.BuildAction;

/**
 * A {@link BuildActionExecuter} responsible for establishing the build tree for a single invocation of a {@link BuildAction}.
 */
public class BuildTreeScopeBuildActionExecuter implements BuildActionExecuter<BuildActionParameters, BuildSessionContext> {
    private final BuildActionExecuter<BuildActionParameters, BuildTreeContext> delegate;

    public BuildTreeScopeBuildActionExecuter(BuildActionExecuter<BuildActionParameters, BuildTreeContext> delegate) {
        this.delegate = delegate;
    }

    @Override
    public BuildActionResult execute(BuildAction action, BuildActionParameters actionParameters, BuildSessionContext buildSession) {
        BuildType buildType = action.isRunTasks() ? BuildType.TASKS : BuildType.MODEL;
        try (BuildTreeState buildTree = new BuildTreeState(buildSession.getBuildSessionServices(), buildType)) {
            return delegate.execute(action, actionParameters, new BuildTreeContext(buildTree));
        }
    }
}
