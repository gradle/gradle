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
public class TaskInputsDeprecationSupport implements TaskInputs {

    private UnsupportedOperationException failWithUnsupportedMethod(String method) {
        throw new UnsupportedOperationException(String.format("Chaining of the TaskInputs.%s method is not supported since Gradle 5.0.", method));
    }

    @Override
    public boolean getHasInputs() {
        throw failWithUnsupportedMethod("getHasInputs()");
    }

    @Override
    public FileCollection getFiles() {
        throw failWithUnsupportedMethod("getFiles()");
    }

    @Override
    public TaskInputFilePropertyBuilder files(Object... paths) {
        throw failWithUnsupportedMethod("files(Object...)");
    }

    @Override
    public TaskInputFilePropertyBuilder file(Object path) {
        throw failWithUnsupportedMethod("file(Object)");
    }

    @Override
    public TaskInputFilePropertyBuilder dir(Object dirPath) {
        throw failWithUnsupportedMethod("dir(Object)");
    }

    @Override
    public Map<String, Object> getProperties() {
        throw failWithUnsupportedMethod("getProperties()");
    }

    @Override
    public TaskInputPropertyBuilder property(String name, @Nullable Object value) {
        throw failWithUnsupportedMethod("property(String, Object)");
    }

    @Override
    public TaskInputs properties(Map<String, ?> properties) {
        throw failWithUnsupportedMethod("properties(Map)");
    }

    @Override
    public boolean getHasSourceFiles() {
        throw failWithUnsupportedMethod("getHasSourceFiles()");
    }

    @Override
    public FileCollection getSourceFiles() {
        throw failWithUnsupportedMethod("getSourceFiles()");
    }
}
