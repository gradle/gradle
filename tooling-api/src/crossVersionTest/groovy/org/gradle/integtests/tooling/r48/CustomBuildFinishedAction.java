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

package org.gradle.integtests.tooling.r48;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;

public class CustomBuildFinishedAction implements BuildAction<String> {
    // Tasks graph is already calculated and tasks executed. Action or model builders can access tasks results.

    @Override
    public String execute(BuildController controller) {
        // Print something to verify it is after task execution
        System.out.println("buildFinishedAction");
        return controller.getModel(CustomBuildFinishedModel.class).getValue();
    }
}
