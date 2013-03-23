/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.execution;

import org.gradle.api.execution.TaskExecutionContext;
import org.gradle.api.internal.changedetection.ChangeType;

import java.io.File;

public class DefaultInputFileChange implements TaskExecutionContext.InputFileChange {

    private final ChangeType type;
    private final File file;

    public DefaultInputFileChange(File file, ChangeType type) {
        this.file = file;
        this.type = type;
    }

    public boolean isRemoved() {
        return type == ChangeType.REMOVED;
    }

    public boolean isAdded() {
        return type == ChangeType.ADDED;
    }

    public boolean isModified() {
        return type == ChangeType.MODIFIED;
    }

    public File getFile() {
        return file;
    }
}
