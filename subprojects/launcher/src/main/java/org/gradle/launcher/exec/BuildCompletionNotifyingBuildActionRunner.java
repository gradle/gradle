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

import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.scan.eob.DefaultBuildScanEndOfBuildNotifier;

/**
 * An {@link BuildActionRunner} that wraps all work in a build operation.
 */
public class BuildCompletionNotifyingBuildActionRunner implements BuildActionRunner {
    private final BuildActionRunner delegate;

    public BuildCompletionNotifyingBuildActionRunner(BuildActionRunner delegate) {
        this.delegate = delegate;
    }

    @Override
    public Result run(final BuildAction action, final BuildController buildController) {
        DefaultBuildScanEndOfBuildNotifier endOfBuildNotifier = buildController.getGradle().getServices().get(DefaultBuildScanEndOfBuildNotifier.class);
        Result result;
        try {
            result = delegate.run(action, buildController);
        } catch (RuntimeException e) {
            result = Result.failed(e);
        }
        endOfBuildNotifier.fireBuildComplete(result.getBuildFailure());
        return result;
    }
}
