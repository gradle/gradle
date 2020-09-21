/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.internal.execution.OutputSnapshotter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.vfs.FileSystemAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class DefaultOutputSnapshotter implements OutputSnapshotter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultOutputSnapshotter.class);

    private final FileSystemAccess fileSystemAccess;

    public DefaultOutputSnapshotter(FileSystemAccess fileSystemAccess) {
        this.fileSystemAccess = fileSystemAccess;
    }

    @Override
    public ImmutableSortedMap<String, FileSystemSnapshot> snapshotOutputs(UnitOfWork work) {
        ImmutableSortedMap.Builder<String, FileSystemSnapshot> builder = ImmutableSortedMap.naturalOrder();
        work.visitOutputProperties((propertyName, type, root) -> {
            LOGGER.debug("Snapshotting property {} for {}", propertyName, work);
            CompleteFileSystemLocationSnapshot result = fileSystemAccess.read(root.getAbsolutePath(), Function.identity());
            builder.put(propertyName, result);
        });
        return builder.build();
    }
}
