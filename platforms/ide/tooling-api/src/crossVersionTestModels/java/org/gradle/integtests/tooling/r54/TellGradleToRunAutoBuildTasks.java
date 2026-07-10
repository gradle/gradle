/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.tooling.r54;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.eclipse.RunEclipseAutoBuildTasks;

import java.io.Serializable;

public class TellGradleToRunAutoBuildTasks implements BuildAction<Void>, Serializable {

    @Override
    public Void execute(BuildController controller) {
            controller.getModel(RunEclipseAutoBuildTasks.class);
        return null;
    }
}
