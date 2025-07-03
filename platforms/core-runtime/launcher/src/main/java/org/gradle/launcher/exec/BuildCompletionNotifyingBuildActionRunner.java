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

import org.gradle.execution.MultipleBuildFailures;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.problems.failure.FailureFactory;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * An {@link BuildActionRunner} that notifies the GE plugin manager that the build has completed.
 */
public class BuildCompletionNotifyingBuildActionRunner implements BuildActionRunner {
    private final BuildActionRunner delegate;
    private final GradleEnterprisePluginManager gradleEnterprisePluginManager;
    private final FailureFactory failureFactory;

    public BuildCompletionNotifyingBuildActionRunner(
        GradleEnterprisePluginManager gradleEnterprisePluginManager,
        FailureFactory failureFactory,
        BuildActionRunner delegate
    ) {
        this.gradleEnterprisePluginManager = gradleEnterprisePluginManager;
        this.failureFactory = failureFactory;
        this.delegate = delegate;
    }

    @Override
    public Result run(final BuildAction action, BuildTreeLifecycleController buildController) {
        Result result;
        try {
            result = delegate.run(action, buildController);
        } catch (Throwable t) {
            // Note: throw the failure rather than returning a result object containing the failure, as console failure logging based on the _result_ happens down in the root build scope
            // whereas console failure logging based on the _thrown exception_ happens up outside session scope. It would be better to refactor so that a result can be returned from here
            notifyEnterprisePluginManager(Result.failed(t, failureFactory.create(t)));
            throw UncheckedException.throwAsUncheckedException(t);
        }
        notifyEnterprisePluginManager(result);
        return result;
    }

    private void notifyEnterprisePluginManager(Result result) {
        // Validate the invariant, but avoid failing in production to allow Develocity to receive _a_ result
        // to provide a better user experience in the face of a bug on the Gradle side
        assert result.getBuildFailure() == null || result.getRichBuildFailure() != null
            : "Rich build failure must not be null when build failure is present. Build failure: " + result.getBuildFailure();
        List<Failure> unwrappedBuildFailure = unwrapBuildFailure(result.getRichBuildFailure());
        gradleEnterprisePluginManager.buildFinished(result.getBuildFailure(), unwrappedBuildFailure);
    }

    @Nullable
    private static List<Failure> unwrapBuildFailure(@Nullable Failure richBuildFailure) {
        if (richBuildFailure == null) {
            // No build failure
            return null;
        }
        return richBuildFailure.getOriginal() instanceof MultipleBuildFailures
            ? richBuildFailure.getCauses()
            : Collections.singletonList(richBuildFailure);
    }
}
