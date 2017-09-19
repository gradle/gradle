/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskInputFilePropertyBuilder;
import org.gradle.api.tasks.TaskInputPropertyBuilder;
import org.gradle.api.tasks.TaskInputs;

import javax.annotation.Nullable;
import java.util.Map;

@NonNullApi
abstract class TaskInputsDeprecationSupport implements TaskInputs {
    // --- See CompatibilityAdapterForTaskInputs for an explanation for why these methods are here

    protected abstract TaskInputs getTaskInputs(String method);

    @Override
    public boolean getHasInputs() {
        return getTaskInputs("getHasInputs()").getHasInputs();
    }

    @Override
    public FileCollection getFiles() {
        return getTaskInputs("getFiles()").getFiles();
    }

    @Override
    public TaskInputFilePropertyBuilder files(Object... paths) {
        return getTaskInputs("files(Object...)").files(paths);
    }

    @Override
    public TaskInputFilePropertyBuilder file(Object path) {
        return getTaskInputs("file(Object)").file(path);
    }

    @Override
    public TaskInputFilePropertyBuilder dir(Object dirPath) {
        return getTaskInputs("dir(Object)").dir(dirPath);
    }

    @Override
    public Map<String, Object> getProperties() {
        return getTaskInputs("getProperties()").getProperties();
    }

    @Override
    public TaskInputPropertyBuilder property(String name, @Nullable Object value) {
        return getTaskInputs("property(String, Object)").property(name, value);
    }

    @Override
    public TaskInputs properties(Map<String, ?> properties) {
        return getTaskInputs("properties(Map)").properties(properties);
    }

    @Override
    public boolean getHasSourceFiles() {
        return getTaskInputs("getHasSourceFiles()").getHasSourceFiles();
    }

    @Override
    public FileCollection getSourceFiles() {
        return getTaskInputs("getSourceFiles()").getSourceFiles();
    }
}
