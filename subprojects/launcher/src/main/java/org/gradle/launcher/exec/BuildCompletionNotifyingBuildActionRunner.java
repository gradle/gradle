/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.internal.UncheckedException;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;
import org.gradle.internal.invocation.BuildAction;

/**
 * An {@link BuildActionRunner} that notifies the GE plugin manager that the build has completed.
 */
public class BuildCompletionNotifyingBuildActionRunner implements BuildActionRunner {
    private final BuildActionRunner delegate;
    private final GradleEnterprisePluginManager gradleEnterprisePluginManager;

    public BuildCompletionNotifyingBuildActionRunner(BuildActionRunner delegate, GradleEnterprisePluginManager gradleEnterprisePluginManager) {
        this.delegate = delegate;
        this.gradleEnterprisePluginManager = gradleEnterprisePluginManager;
    }

    @Override
    public Result run(final BuildAction action, BuildTreeLifecycleController buildController) {
        Result result;
        try {
            result = delegate.run(action, buildController);
        } catch (Throwable t) {
            // Note: throw the failure rather than returning a result object containing the failure, as console failure logging based on the _result_ happens down in the root build scope
            // whereas console failure logging based on the _thrown exception_ happens up outside session scope. It would be better to refactor so that a result can be returned from here
            notifyEnterprisePluginManager(Result.failed(t));
            throw UncheckedException.throwAsUncheckedException(t);
        }
        notifyEnterprisePluginManager(result);
        return result;
    }

    private void notifyEnterprisePluginManager(Result result) {
        gradleEnterprisePluginManager.buildFinished(result.getBuildFailure());
    }
}
