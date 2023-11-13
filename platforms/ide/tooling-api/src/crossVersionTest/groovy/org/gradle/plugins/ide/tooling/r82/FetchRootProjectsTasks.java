/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r82;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.Task;

import java.util.List;

import static java.util.stream.Collectors.toList;

public class FetchRootProjectsTasks implements BuildAction<List<Task>> {
    @Override
    public List<Task> execute(BuildController controller) {
        return controller.getBuildModel().getEditableBuilds().stream()
            .flatMap(build -> controller.getModel(build.getRootProject(), GradleProject.class).getTasks().stream())
            .collect(toList());
    }
}
