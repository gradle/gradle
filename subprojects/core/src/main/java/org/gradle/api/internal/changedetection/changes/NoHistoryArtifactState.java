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

package org.gradle.api.internal.changedetection.changes;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.cache.TaskCacheKey;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

class NoHistoryArtifactState implements TaskArtifactState, TaskExecutionHistory {
    public boolean isUpToDate(Collection<String> messages) {
        if (messages != null) {
            messages.add("Task has not declared any outputs.");
        }
        return false;
    }

    public IncrementalTaskInputs getInputChanges() {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskCacheKey calculateCacheKey() {
        return null;
    }

    public TaskExecutionHistory getExecutionHistory() {
        return this;
    }

    public void beforeTask() {
    }

    public void afterTask() {
    }

    public void finished() {
    }

    public FileCollection getOutputFiles() {
        return EmptyFileCollection.INSTANCE;
    }


    private static class EmptyFileCollection extends FileCollectionAdapter implements Serializable {

        private static final long serialVersionUID = -5671359228892430322L;

        private static final FileCollection INSTANCE = new EmptyFileCollection();

        private EmptyFileCollection() {
            super(EmptyFileSet.INSTANCE);
        }

        private Object readResolve() {
            return INSTANCE;
        }
    }

    private static class EmptyFileSet implements MinimalFileSet, Serializable {

        private static final long serialVersionUID = 8533471127662131385L;

        private static final Set<File> EMPTY_FILE_SET = Collections.emptySet();
        private static final MinimalFileSet INSTANCE = new EmptyFileSet();

        private EmptyFileSet() {
        }

        @Override
        public Set<File> getFiles() {
            return EMPTY_FILE_SET;
        }

        @Override
        public String getDisplayName() {
            return "empty file collection";
        }

        private Object readResolve() {
            return INSTANCE;
        }
    }
}
