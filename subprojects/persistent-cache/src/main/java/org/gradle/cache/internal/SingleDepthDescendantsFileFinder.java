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

package org.gradle.cache.internal;

import com.google.common.base.Preconditions;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SingleDepthDescendantsFileFinder implements EligibleFilesFinder {
    private final int depth;

    public SingleDepthDescendantsFileFinder(int depth) {
        Preconditions.checkArgument(depth > 0, "depth must be > 0: %s", depth);
        this.depth = depth;
    }

    @Override
    public File[] find(File baseDir, FileFilter filter) {
        List<File> result = new ArrayList<File>();
        find(baseDir, filter, 1, result);
        return result.toArray(new File[0]);
    }

    private void find(File baseDir, FileFilter filter, int currentDepth, List<File> result) {
        List<File> files = listFiles(baseDir, filter);
        if (depth == currentDepth) {
            result.addAll(files);
        }
        for (File fileInBaseDir : files) {
            if (fileInBaseDir.isDirectory()) {
                find(fileInBaseDir, filter, currentDepth + 1, result);
            }
        }
    }

    private List<File> listFiles(File baseDir, FileFilter filter) {
        File[] files = baseDir.listFiles(filter);
        return files == null ? Collections.<File>emptyList() : Arrays.asList(files);
    }
}
