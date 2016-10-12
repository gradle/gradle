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

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.internal.FileUtils;
import org.gradle.internal.classloader.ClassPathSnapshot;
import org.gradle.internal.classloader.ClassPathSnapshotter;
import org.gradle.internal.classpath.ClassPath;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class HashClassPathSnapshotter implements ClassPathSnapshotter {

    private final FileHasher hasher;

    public HashClassPathSnapshotter(FileHasher hasher) {
        this.hasher = hasher;
    }

    @Override
    public ClassPathSnapshot snapshot(ClassPath classPath) {
        final List<String> visitedFilePaths = Lists.newLinkedList();
        final Set<File> visitedDirs = Sets.newLinkedHashSet();
        final List<File> cpFiles = classPath.getAsFiles();
        com.google.common.hash.Hasher checksum = Hashing.md5().newHasher();
        hash(checksum, visitedFilePaths, visitedDirs, cpFiles.iterator());
        return new HashClassPathSnapshot(visitedFilePaths, checksum.hash());
    }

    private void hash(com.google.common.hash.Hasher combinedHash, List<String> visitedFilePaths, Set<File> visitedDirs, Iterator<File> toHash) {
        while (toHash.hasNext()) {
            File file = FileUtils.canonicalize(toHash.next());
            if (file.isDirectory()) {
                if (visitedDirs.add(file)) {
                    //in theory, awkward symbolic links can lead to recursion problems.
                    //TODO - figure out a way to test it. I only tested it 'manually' and the feature is needed.
                    File[] sortedFiles = file.listFiles();
                    Arrays.sort(sortedFiles);
                    hash(combinedHash, visitedFilePaths, visitedDirs, Iterators.forArray(sortedFiles));
                }
            } else if (file.isFile()) {
                visitedFilePaths.add(file.getAbsolutePath());
                combinedHash.putBytes(hasher.hash(file).asBytes());
            }
            //else an empty folder - a legit situation
        }
    }

    private static class HashClassPathSnapshot implements ClassPathSnapshot {
        private final List<String> files;
        private final HashCode hash;

        public HashClassPathSnapshot(List<String> files, HashCode hash) {
            assert files != null;
            this.files = files;
            this.hash = hash;
        }

        @Override
        public HashCode getStrongHash() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            HashClassPathSnapshot that = (HashClassPathSnapshot) o;

            return hash.equals(that.hash) && files.equals(that.files);
        }

        @Override
        public int hashCode() {
            int result = files.hashCode();
            result = 31 * result + hash.hashCode();
            return result;
        }
    }
}
