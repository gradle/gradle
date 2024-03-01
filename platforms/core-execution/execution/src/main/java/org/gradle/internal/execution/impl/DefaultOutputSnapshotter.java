/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.execution.impl;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.FileCollectionSnapshotter;
import org.gradle.internal.execution.OutputSnapshotter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.io.File;

public class DefaultOutputSnapshotter implements OutputSnapshotter {
    private final FileCollectionSnapshotter fileCollectionSnapshotter;

    public DefaultOutputSnapshotter(FileCollectionSnapshotter fileCollectionSnapshotter) {
        this.fileCollectionSnapshotter = fileCollectionSnapshotter;
    }

    @Override
    public ImmutableSortedMap<String, FileSystemSnapshot> snapshotOutputs(UnitOfWork work, File workspace) {
        ImmutableSortedMap.Builder<String, FileSystemSnapshot> builder = ImmutableSortedMap.naturalOrder();
        work.visitOutputs(workspace, new UnitOfWork.OutputVisitor() {
            @Override
            public void visitOutputProperty(String propertyName, TreeType type, UnitOfWork.OutputFileValueSupplier value) {
                FileSystemSnapshot snapshot;
                try {
                    snapshot = fileCollectionSnapshotter.snapshot(value.getFiles()).getSnapshot();
                } catch (Exception ex) {
                    throw new OutputFileSnapshottingException(propertyName, ex);
                }
                builder.put(propertyName, snapshot);
            }
        });
        return builder.build();
    }
}
