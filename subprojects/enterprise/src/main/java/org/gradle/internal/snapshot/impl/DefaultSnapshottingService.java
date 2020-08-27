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

package org.gradle.internal.snapshot.impl;

import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.Snapshot;
import org.gradle.internal.snapshot.SnapshottingService;
import org.gradle.internal.vfs.FileSystemAccess;

import javax.inject.Inject;
import java.nio.file.Path;

import static java.lang.String.format;

public class DefaultSnapshottingService implements SnapshottingService {

    private final FileSystemAccess fileSystemAccess;

    @Inject
    public DefaultSnapshottingService(FileSystemAccess fileSystemAccess) {
        this.fileSystemAccess = fileSystemAccess;
    }

    @Override
    public Snapshot snapshotFor(Path filePath) {
        String absolutePath = filePath.toAbsolutePath().toString();
        HashCode hash = fileSystemAccess.read(absolutePath, CompleteFileSystemLocationSnapshot::getHash);

        return new DefaultSnapshot(hash);
    }

    private static class DefaultSnapshot implements Snapshot {

        private final HashCode hashCode;

        public DefaultSnapshot(HashCode hashCode) {
            this.hashCode = hashCode;
        }

        @Override
        public String getHashValue() {
            return hashCode.toString();
        }

        @Override
        public String toString() {
            return format("DefaultSnapshot { hashValue='%s' }", getHashValue());
        }
    }
}
