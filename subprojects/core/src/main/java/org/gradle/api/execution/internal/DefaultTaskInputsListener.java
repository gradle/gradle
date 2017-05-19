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

package org.gradle.api.execution.internal;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.internal.filewatch.FileSystemChangeWaiter;

public class DefaultTaskInputsListener implements TaskInputsListener {
    private FileSystemChangeWaiter waiter;

    @Override
    public void onExecute(TaskInternal taskInternal, FileCollectionInternal fileSystemInputs) {
        if (waiter!=null) {
            FileSystemSubset.Builder fileSystemSubsetBuilder = FileSystemSubset.builder();
            fileSystemInputs.registerWatchPoints(fileSystemSubsetBuilder);
            waiter.watch(fileSystemSubsetBuilder.build());
        }
    }

    @Override
    public void setFileSystemWaiter(FileSystemChangeWaiter waiter) {
        this.waiter = waiter;
    }
}
