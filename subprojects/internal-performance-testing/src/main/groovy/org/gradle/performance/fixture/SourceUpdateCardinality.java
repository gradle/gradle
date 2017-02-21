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

package org.gradle.performance.fixture;

import org.gradle.api.Action;

import java.io.File;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;

import static org.gradle.internal.FileUtils.hasExtension;

public enum SourceUpdateCardinality {
    ONE_FILE(1, "one file is updated"),
    ALL_FILES(Integer.MAX_VALUE, "all files are updated");

    private final int maxNumberOfUpdatedSourceFiles;
    private final String description;

    SourceUpdateCardinality(int maxNumberOfUpdatedSourceFiles, String description) {
        this.maxNumberOfUpdatedSourceFiles = maxNumberOfUpdatedSourceFiles;
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public void onSourceFile(File dir, String extension, Action<? super File> action) {
        onSourceFile(dir, extension, false, action);
    }

    public void onSourceFile(File dir, String extension, boolean recurse, Action<? super File> action) {
        int idx = 0;
        Deque<File> queue = new LinkedList<File>();
        Collections.addAll(queue, dir.listFiles());
        while (!queue.isEmpty()) {
            File f = queue.removeFirst();
            if (hasExtension(f, extension)) {
                action.execute(f);
                if (++idx == maxNumberOfUpdatedSourceFiles) {
                    return;
                }
            } else if (recurse && f.isDirectory()) {
                Collections.addAll(queue, f.listFiles());
            }
        }
    }
}
