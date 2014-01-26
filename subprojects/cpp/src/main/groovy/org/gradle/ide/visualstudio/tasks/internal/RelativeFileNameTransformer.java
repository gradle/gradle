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

package org.gradle.ide.visualstudio.tasks.internal;

import org.gradle.api.Transformer;

import java.io.File;
import java.io.IOException;

public class RelativeFileNameTransformer implements Transformer<String, File> {
    private final File rootDir;
    private final File currentDir;

    public RelativeFileNameTransformer(File rootDir, File relative) {
        this.rootDir = rootDir;
        if (relative.isDirectory()) {
            this.currentDir = relative;
        } else {
            this.currentDir = relative.getParentFile();
        }
    }

    public String transform(File file) {
        String canonicalRoot;
        String canonicalCurrent;
        String canonicalFile;
        try {
            canonicalRoot = rootDir.getCanonicalPath();
            canonicalCurrent = currentDir.getCanonicalPath();
            canonicalFile = file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }

        if (!canonicalCurrent.contains(canonicalRoot) || !canonicalFile.contains(canonicalRoot)) {
            return file.getAbsolutePath();
        }

        String relativeFile = canonicalFile.substring(canonicalRoot.length());
        return findPathUpTo(new File(canonicalCurrent), new File(canonicalRoot)) + relativeFile;
    }

    private String findPathUpTo(File from, File to) {
        if (from.getParentFile() == null) {
            throw new IllegalStateException("We've already verified that this should never happen");
        }
        if (from.getParentFile().equals(to)) {
            return "..";
        }
        String parentPathUpTo = findPathUpTo(from.getParentFile(), to);
        if (parentPathUpTo == null) {
            return null;
        }
        return ".." + File.separator + parentPathUpTo;
    }
}