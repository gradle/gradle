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

package org.gradle.api.internal.changedetection.rules;

import java.io.File;

abstract class FileChange implements TaskStateChange {
    private final String path;
    private final ChangeType change;

    public FileChange(String path, ChangeType change) {
        this.path = path;
        this.change = change;
    }

    public String getMessage() {
        return String.format("%s file %s %s.", getFileType(), path, change.describe());
    }

    @Override
    public String toString() {
        return getMessage();
    }

    protected abstract String getFileType();

    public String getPath() {
        return path;
    }

    public File getFile() {
        return new File(path);
    }

    public boolean isAdded() {
        return change == ChangeType.ADDED;
    }

    public boolean isModified() {
        return change == ChangeType.MODIFIED;
    }

    public boolean isRemoved() {
        return change == ChangeType.REMOVED;
    }
}
