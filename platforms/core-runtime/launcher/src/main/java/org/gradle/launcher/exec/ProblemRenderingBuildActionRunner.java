/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.problems.internal.DefaultProblemProgressDetails;
import org.gradle.api.problems.internal.Problem;
import org.gradle.api.problems.internal.ProblemProgressDetails;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.problems.internal.rendering.ProblemRenderer;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class ProblemRenderingBuildActionRunner implements BuildActionRunner, BuildOperationListener {

    private final List<Problem> problems = new ArrayList<>();
    private final BuildOperationListenerManager listenerManager;
    private final BuildActionRunner delegate;
    private final ProblemRenderer renderer;

    public ProblemRenderingBuildActionRunner(BuildOperationListenerManager listenerManager, BuildActionRunner delegate) {
        this.listenerManager = listenerManager;
        this.delegate = delegate;
        this.renderer = new ProblemRenderer();
    }

    @Nonnull
    @Override
    public Result run(@Nonnull BuildAction action, @Nonnull BuildTreeLifecycleController buildController) {
        listenerManager.addListener(this);
        Result result = delegate.run(action, buildController);
        listenerManager.removeListener(this);

        renderer.render(problems);
        return result;
    }

    @Override
    public void started(@Nonnull BuildOperationDescriptor buildOperation, @Nonnull OperationStartEvent startEvent) {
        // No-op: we are not interested in start events
    }

    @Override
    public void progress(@Nonnull OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
        if (progressEvent.getDetails() instanceof ProblemProgressDetails) {
            DefaultProblemProgressDetails details = (DefaultProblemProgressDetails) progressEvent.getDetails();
            problems.add(details.getProblem());
        }
    }

    @Override
    public void finished(@Nonnull BuildOperationDescriptor buildOperation, @Nonnull OperationFinishEvent finishEvent) {
        // No-op: we are not interested in finish events
    }
}
