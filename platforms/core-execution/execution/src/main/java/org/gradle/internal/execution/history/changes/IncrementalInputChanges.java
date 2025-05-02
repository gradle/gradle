/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.history.changes;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.internal.Cast;
import org.gradle.work.FileChange;

public class IncrementalInputChanges implements InputChangesInternal {

    private final InputFileChanges changes;
    private final IncrementalInputProperties incrementalInputProperties;

    public IncrementalInputChanges(InputFileChanges changes, IncrementalInputProperties incrementalInputProperties) {
        this.changes = changes;
        this.incrementalInputProperties = incrementalInputProperties;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public Iterable<FileChange> getFileChanges(FileCollection parameter) {
        return getObjectFileChanges(parameter);
    }

    @Override
    public Iterable<FileChange> getFileChanges(Provider<? extends FileSystemLocation> parameter) {
        return getObjectFileChanges(parameter);
    }

    private Iterable<FileChange> getObjectFileChanges(Object parameter) {
        String propertyName = incrementalInputProperties.getPropertyNameFor(parameter);
        CollectingChangeVisitor visitor = new CollectingChangeVisitor();
        changes.accept(propertyName, visitor);
        return Cast.uncheckedNonnullCast(visitor.getChanges());
    }

    @Override
    public Iterable<InputFileDetails> getAllFileChanges() {
        CollectingChangeVisitor visitor = new CollectingChangeVisitor();
        changes.accept(visitor);
        return Cast.uncheckedNonnullCast(visitor.getChanges());
    }
}
