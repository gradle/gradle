/*
 * Copyright 2014 the original author or authors.
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

import java.util.List;

public class ChainingBuildActionRunner implements BuildActionRunner {
    private List<? extends BuildActionRunner> runners;

    public ChainingBuildActionRunner(List<? extends BuildActionRunner> runners) {
        this.runners = runners;
    }

    @Override
    public void run(BuildAction action, BuildController buildController) {
        for (BuildActionRunner runner : runners) {
            runner.run(action, buildController);
            if (buildController.hasResult()) {
                return;
            }
        }
    }
}
