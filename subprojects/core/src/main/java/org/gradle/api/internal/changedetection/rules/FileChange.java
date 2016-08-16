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

import com.google.common.base.Objects;
import org.gradle.api.tasks.incremental.InputFileDetails;

import java.io.File;

public class FileChange implements TaskStateChange, InputFileDetails {
    private final String path;
    private final ChangeType change;
    private final String fileType;

    public FileChange(String path, ChangeType change, String fileType) {
        this.path = path;
        this.change = change;
        this.fileType = fileType;
    }

    public String getMessage() {
        return fileType + " file " + path + " " + change.describe() + ".";
    }

    @Override
    public String toString() {
        return getMessage();
    }

    public String getPath() {
        return path;
    }

    public File getFile() {
        return new File(path);
    }

    public ChangeType getType() {
        return change;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FileChange that = (FileChange) o;
        return Objects.equal(path, that.path)
            && change == that.change
            && Objects.equal(fileType, that.fileType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path, change, fileType);
    }
}
