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
import org.gradle.internal.file.FileType;

import java.io.File;

public class FileChange implements TaskStateChange, InputFileDetails {
    private final String path;
    private final ChangeType change;
    private final String title;
    private final FileType previousFileType;
    private final FileType currentFileType;

    public static FileChange added(String path, String title, FileType currentFileType) {
        return new FileChange(path, ChangeType.ADDED, title, FileType.Missing, currentFileType);
    }

    public static FileChange removed(String path, String title, FileType previousFileType) {
        return new FileChange(path, ChangeType.REMOVED, title, previousFileType, FileType.Missing);
    }

    public static FileChange modified(String path, String title, FileType previousFileType, FileType currentFileType) {
        return new FileChange(path, ChangeType.MODIFIED, title, previousFileType, currentFileType);
    }

    private FileChange(String path, ChangeType change, String title, FileType previousFileType, FileType currentFileType) {
        this.path = path;
        this.change = change;
        this.title = title;
        this.previousFileType = previousFileType;
        this.currentFileType = currentFileType;
    }

    public String getMessage() {
        return title + " file " + path + " " + getDisplayedChangeType().describe() + ".";
    }

    private ChangeType getDisplayedChangeType() {
        if (change != ChangeType.MODIFIED) {
            return change;
        }
        if (previousFileType == FileType.Missing) {
            return ChangeType.ADDED;
        }
        if (currentFileType == FileType.Missing) {
            return ChangeType.REMOVED;
        }
        return ChangeType.MODIFIED;
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
            && Objects.equal(title, that.title)
            && Objects.equal(previousFileType, that.previousFileType)
            && Objects.equal(currentFileType, that.currentFileType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path, change, title, previousFileType, currentFileType);
    }
}
