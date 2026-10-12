/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.internal.cc.impl.isolated;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.eclipse.RunEclipseAutoBuildTasks;

import java.io.Serializable;

/**
 * Requests the model that makes Gradle run the tasks registered via {@code eclipse.autoBuildTasks}.
 *
 * <p>This mirrors what Buildship does when the Eclipse workspace triggers an auto build. It goes
 * through the same builder as {@link RunEclipseSynchronizationTasksAction}, but reads a different
 * property of the Eclipse extension of every project.
 */
public class RunEclipseAutoBuildTasksAction implements BuildAction<Void>, Serializable {

    @Override
    public Void execute(BuildController controller) {
        controller.getModel(RunEclipseAutoBuildTasks.class);
        return null;
    }
}
