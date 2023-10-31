/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.Task;
import org.gradle.api.internal.tasks.PublicTaskSpecification;
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleTask;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;

public abstract class ToolingModelBuilderSupport {
    public static <T extends LaunchableGradleTask> T buildFromTask(T target, DefaultProjectIdentifier projectIdentifier, Task task) {
        target.setPath(task.getPath())
                .setName(task.getName())
                .setGroup(task.getGroup())
                .setDisplayName(task.toString())
                .setDescription(task.getDescription())
                .setPublic(PublicTaskSpecification.INSTANCE.isSatisfiedBy(task))
                .setProjectIdentifier(projectIdentifier);
        return target;
    }

    public static <T extends LaunchableGradleTask> T buildFromTaskModel(T target, LaunchableGradleTask model) {
        target.setPath(model.getPath())
            .setName(model.getName())
            .setGroup(model.getGroup())
            .setDisplayName(model.getDisplayName())
            .setDescription(model.getDescription())
            .setPublic(model.isPublic())
            .setProjectIdentifier(model.getProjectIdentifier())
            .setBuildTreePath(model.getBuildTreePath());
        return target;
    }
}
