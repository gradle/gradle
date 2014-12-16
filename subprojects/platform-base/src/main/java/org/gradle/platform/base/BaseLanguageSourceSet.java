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

package org.gradle.platform.base;

import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.Task;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.base.LanguageSourceSet;

/**
 * Base class for custom language sourceset implementations.
 * A custom implementation of {@link org.gradle.language.base.LanguageSourceSet} must extend this type.
 */
public class BaseLanguageSourceSet implements LanguageSourceSet {

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public SourceDirectorySet getSource() {
        return null;
    }

    @Override
    public void source(Action<? super SourceDirectorySet> config) {

    }

    @Override
    public void generatedBy(Task generatorTask) {

    }

    @Nullable
    @Override
    public Task getBuildTask() {
        return null;
    }

    @Override
    public void setBuildTask(Task lifecycleTask) {

    }

    @Override
    public void builtBy(Object... tasks) {

    }

    @Override
    public boolean hasBuildDependencies() {
        return false;
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }
}
