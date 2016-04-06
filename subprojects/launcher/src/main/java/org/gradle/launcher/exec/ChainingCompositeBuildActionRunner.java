/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.composite.CompositeBuildActionParameters;
import org.gradle.internal.composite.CompositeBuildActionRunner;
import org.gradle.internal.composite.CompositeBuildController;
import org.gradle.internal.invocation.BuildAction;

import java.util.List;

public class ChainingCompositeBuildActionRunner implements CompositeBuildActionRunner {
    private final List<? extends CompositeBuildActionRunner> runners;

    public ChainingCompositeBuildActionRunner(List<? extends CompositeBuildActionRunner> runners) {
        this.runners = runners;
    }

    @Override
    public void run(BuildAction action, BuildRequestContext requestContext, CompositeBuildActionParameters actionParameters, CompositeBuildController buildController) {
        for (CompositeBuildActionRunner runner : runners) {
            runner.run(action, requestContext, actionParameters, buildController);
            if (buildController.hasResult()) {
                return;
            }
        }
        throw new UnsupportedOperationException(String.format("Don't know how to run a composite build action of type %s.", action.getClass().getSimpleName()));
    }
}
