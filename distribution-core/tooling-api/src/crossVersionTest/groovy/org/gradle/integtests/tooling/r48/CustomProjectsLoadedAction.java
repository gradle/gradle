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

import org.gradle.api.Action;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;

public class CustomProjectsLoadedAction implements BuildAction<String>, Serializable {
    // Task graph is not calculated yet. Plugins can add tasks to it.

    @Nullable private final List<String> tasks;

    public CustomProjectsLoadedAction(@Nullable List<String> tasks) {
        this.tasks = tasks;
    }

    @Override
    public String execute(BuildController controller) {
        CustomProjectsLoadedModel model;
        if (tasks == null || tasks.isEmpty()) {
            model = controller.getModel(CustomProjectsLoadedModel.class);
        } else {
            model = controller.getModel(CustomProjectsLoadedModel.class, CustomParameter.class, new Action<CustomParameter>() {
                @Override
                public void execute(CustomParameter customParameter) {
                    customParameter.setTasks(tasks);
                }
            });
        }
        return model.getValue();
    }
}
