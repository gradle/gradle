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

import com.google.common.base.Joiner;
import org.gradle.api.Transformer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class RelativeFileNameTransformer implements Transformer<String, File> {
    private final File rootDir;
    private final File currentDir;

    private RelativeFileNameTransformer(File rootDir, File currentDir) {
        this.rootDir = rootDir;
        this.currentDir = currentDir;
    }

    public static Transformer<String, File> forFile(File rootDir, File relativeFile) {
        return new RelativeFileNameTransformer(rootDir, relativeFile.getParentFile());
    }

    public static Transformer<String, File> forDirectory(File rootDir, File currentDirectory) {
        return new RelativeFileNameTransformer(rootDir, currentDirectory);
    }

    public String transform(File file) {
        String canonicalRoot;
        String canonicalFrom;
        String canonicalTo;
        try {
            canonicalRoot = rootDir.getCanonicalPath();
            canonicalFrom = currentDir.getCanonicalPath();
            canonicalTo = file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }

        return findRelativePathInRoot(canonicalRoot, canonicalFrom, canonicalTo);
    }

    private String findRelativePathInRoot(String root, String from, String to) {
        if (!from.contains(root) || !to.contains(root)) {
            return to;
        }

        String relativePath = findRelativePath(from, to);
        return relativePath.length() == 0 ? "." : relativePath;
    }

    private String findRelativePath(String from, String to) {
        List<String> fromPath = splitPath(from);
        List<String> toPath = splitPath(to);
        List<String> relativePath = new ArrayList<String>();

        while (!fromPath.isEmpty() && !toPath.isEmpty() && fromPath.get(0).equals(toPath.get(0))) {
            fromPath.remove(0);
            toPath.remove(0);
        }
        for (String ignored : fromPath) {
            relativePath.add("..");
        }
        for (String entry : toPath) {
            relativePath.add(entry);
        }
        return Joiner.on(File.separatorChar).join(relativePath);
    }

    private List<String> splitPath(String path) {
        File pathFile = new File(path);
        List<String> split = new LinkedList<String>();
        while (pathFile != null) {
            split.add(0, pathFile.getName());
            pathFile = pathFile.getParentFile();
        }
        return split;
    }
}