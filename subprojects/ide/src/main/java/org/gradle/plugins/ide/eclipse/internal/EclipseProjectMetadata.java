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

package org.gradle.plugins.ide.eclipse.internal;

import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.plugins.ide.eclipse.model.EclipseProject;
import org.gradle.plugins.ide.internal.IdeProjectMetadata;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class EclipseProjectMetadata implements IdeProjectMetadata {
    private final EclipseProject eclipseProject;
    private final File projectDir;
    private final TaskProvider<? extends Task> generatorTask;

    public EclipseProjectMetadata(EclipseProject eclipseProject, File projectDir, TaskProvider<? extends Task> generatorTask) {
        this.eclipseProject = eclipseProject;
        this.projectDir = projectDir;
        this.generatorTask = generatorTask;
    }

    @Override
    public DisplayName getDisplayName() {
        return Describables.withTypeAndName("Eclipse project", eclipseProject.getName());
    }

    public String getName() {
        return eclipseProject.getName();
    }

    @Override
    public File getFile() {
        return new File(projectDir, ".project");
    }

    @Override
    public Set<? extends Task> getGeneratorTasks() {
        return Collections.singleton(generatorTask.get());
    }
}
