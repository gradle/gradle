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

package org.gradle.caching.internal.packaging.impl;

import com.google.common.base.CharMatcher;
import org.gradle.internal.file.FilePathUtil;

import java.util.Deque;
import java.util.LinkedList;

public class RelativePathParser {
    private static final CharMatcher IS_SLASH = CharMatcher.is('/');

    private String currentName;
    private int sizeOfCommonPrefix;
    private int rootLength;
    private Deque<String> directoryPaths = new LinkedList<String>();

    public String getRelativePath() {
        return currentName.substring(rootLength);
    }

    public String getName() {
        return currentName.substring(sizeOfCommonPrefix + 1);
    }

    public int nextPath(String nextPath, boolean directory) {
        currentName = directory ? nextPath.substring(0, nextPath.length() - 1): nextPath;
        String lastDirPath = directoryPaths.peekLast();
        sizeOfCommonPrefix = FilePathUtil.sizeOfCommonPrefix(lastDirPath, currentName, 0, '/');
        int directoriesLeft = determineDirectoriesLeft(lastDirPath, sizeOfCommonPrefix);
        for (int i = 0; i < directoriesLeft; i++) {
            directoryPaths.removeLast();
        }
        if (directory && getDepth() > 0) {
            directoryPaths.addLast(currentName);
        }
        return directoriesLeft;
    }

    private int determineDirectoriesLeft(String lastDirPath, int sizeOfCommonPrefix) {
        if (sizeOfCommonPrefix == lastDirPath.length()) {
            return 0;
        }
        int rootDirAdjustment = (sizeOfCommonPrefix == 0) ? 1 : 0;
        return rootDirAdjustment + IS_SLASH.countIn(lastDirPath.substring(sizeOfCommonPrefix));
    }

    public void rootPath(String path) {
        directoryPaths.addLast(path.substring(0, path.length() - 1));
        rootLength = path.length();
    }

    public int getDepth() {
        return directoryPaths.size();
    }

}
