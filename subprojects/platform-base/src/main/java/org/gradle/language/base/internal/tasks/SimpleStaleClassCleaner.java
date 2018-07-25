/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.language.base.internal.tasks;

import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import org.gradle.api.internal.TaskOutputsInternal;

import java.io.File;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

public class SimpleStaleClassCleaner extends StaleClassCleaner {
    private final Set<File> filesToDelete;
    private final Set<File> toClean = Sets.newHashSet();
    private final Set<String> prefixes = Sets.newHashSet();
    private final Queue<File> directoriesToDelete = new PriorityQueue<File>(10, Ordering.natural().reverse());
    private boolean didWork;

    public SimpleStaleClassCleaner(TaskOutputsInternal taskOutputs) {
        this(taskOutputs.getPreviousOutputFiles());
    }

    public SimpleStaleClassCleaner(Set<File> filesToDelete) {
        this.filesToDelete = filesToDelete;
    }

    @Override
    public void addDirToClean(File toClean) {
        this.toClean.add(toClean);
        prefixes.add(toClean.getAbsolutePath() + File.separator);
    }

    @Override
    public void execute() {
        for (File f : filesToDelete) {
            for (String prefix : prefixes) {
                if (f.getAbsolutePath().startsWith(prefix) && f.isFile()) {
                    didWork |= f.delete();
                    markParentDir(f);
                }
            }
        }
        while (!directoriesToDelete.isEmpty()) {
            File directory = directoriesToDelete.poll();
            if (isEmpty(directory)) {
                didWork |= directory.delete();
                markParentDir(directory);
            }
        }
    }

    private void markParentDir(File f) {
        File parentDir = f.getParentFile();
        if (parentDir != null && !toClean.contains(parentDir)) {
            directoriesToDelete.add(parentDir);
        }
    }

    private boolean isEmpty(File parentDir) {
        String[] children = parentDir.list();
        return children != null && children.length == 0;
    }

    public boolean getDidWork() {
        return didWork;
    }
}
