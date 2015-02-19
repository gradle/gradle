/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.initialization.loadercache;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.internal.changedetection.state.FileSnapshotter;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.List;
import java.util.Set;

public class HashClassPathSnapshotter implements ClassPathSnapshotter {

    private final FileSnapshotter fileSnapshotter;

    public HashClassPathSnapshotter(FileSnapshotter fileSnapshotter) {
        this.fileSnapshotter = fileSnapshotter;
    }

    public ClassPathSnapshot snapshot(ClassPath classPath) {
        List<String> visitedFilePaths = Lists.newLinkedList();
        Set<File> visitedDirs = Sets.newLinkedHashSet();
        List<byte[]> combinedHash = Lists.newLinkedList();
        List<File> cpFiles = classPath.getAsFiles();
        hash(combinedHash, visitedFilePaths, visitedDirs, cpFiles.toArray(new File[cpFiles.size()]));
        return new ClassPathSnapshotImpl(visitedFilePaths, combinedHash);
    }

    private void hash(List<byte[]> combinedHash, List<String> visitedFilePaths, Set<File> visitedDirs, File[] toHash) {
        for (File file : toHash) {
            file = GFileUtils.canonicalise(file);
            if (file.isDirectory()) {
                if (visitedDirs.add(file)) {
                    //in theory, awkward symbolic links can lead to recursion problems.
                    //TODO - figure out a way to test it. I only tested it 'manually' and the feature is needed.
                    hash(combinedHash, visitedFilePaths, visitedDirs, file.listFiles());
                }
            } else if (file.isFile()) {
                visitedFilePaths.add(file.getAbsolutePath());
                combinedHash.add(fileSnapshotter.snapshot(file).getHash());
            }
            //else an empty folder - a legit situation
        }
    }

    private static class ClassPathSnapshotImpl implements ClassPathSnapshot {
        private final List<String> files;
        private final List<byte[]> combinedHash;

        public ClassPathSnapshotImpl(List<String> files, List<byte[]> combinedHash) {
            assert files != null;
            assert combinedHash != null;

            this.files = files;
            this.combinedHash = combinedHash;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ClassPathSnapshotImpl)) {
                return false;
            }
            ClassPathSnapshotImpl that = (ClassPathSnapshotImpl) o;
            return this.files.equals(that.files) && this.combinedHash.equals(that.combinedHash);
        }

        public int hashCode() {
            int result = files.hashCode();
            result = 31 * result + combinedHash.hashCode();
            return result;
        }
    }
}