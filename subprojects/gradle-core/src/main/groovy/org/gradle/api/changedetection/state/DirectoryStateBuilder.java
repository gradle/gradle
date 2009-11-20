/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.changedetection.state;

import org.apache.commons.lang.StringUtils;

import java.io.File;

/**
 * @author Tom Eyckmans
 */
class DirectoryStateBuilder {
    private final StateFileUtil stateFileUtil;

    private File directory = null;

    DirectoryStateBuilder(StateFileUtil stateFileUtil) {
        if (stateFileUtil == null) {
            throw new IllegalArgumentException("stateFileUtil is null!");
        }

        this.stateFileUtil = stateFileUtil;
    }

    public void setDirectory(File directory) {
        if (directory == null) {
            throw new IllegalArgumentException("directory is null!");
        }
        if (!directory.exists()) {
            throw new IllegalArgumentException("directory doesn't exists!");
        }
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("directory is not a directory!");
        }

        this.directory = directory;
    }

    public DirectoryStateBuilder directory(File directory) {
        setDirectory(directory);

        return this;
    }

    public DirectoryState getDirectoryState() {
        if (directory == null) {
            throw new IllegalStateException("directory is null!");
        }

        final String relativePath = stateFileUtil.getRelativePathToDirectoryToProcess(directory);
        final String relativePathDigest = stateFileUtil.getStringDigest(relativePath);
        final int level = StringUtils.countMatches(relativePath, System.getProperty("file.separator"));

        final DirectoryState directoryState = new DirectoryState(directory, relativePath, relativePathDigest, level);

        directory = null;

        return directoryState;
    }
}
