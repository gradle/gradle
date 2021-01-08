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

package org.gradle.ide.xcode.internal;

import org.gradle.api.Task;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.plugins.ide.internal.IdeProjectMetadata;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class XcodeProjectMetadata implements IdeProjectMetadata {
    private final DefaultXcodeProject xcodeProject;
    private final Task projectTask;

    public XcodeProjectMetadata(DefaultXcodeProject xcodeProject, Task projectTask) {
        this.xcodeProject = xcodeProject;
        this.projectTask = projectTask;
    }

    @Override
    public DisplayName getDisplayName() {
        return Describables.withTypeAndName("Xcode project", projectTask.getProject().getName());
    }

    @Override
    public Set<? extends Task> getGeneratorTasks() {
        return Collections.singleton(projectTask);
    }

    @Override
    public File getFile() {
        return xcodeProject.getLocationDir();
    }
}
