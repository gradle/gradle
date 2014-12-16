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

import com.google.common.primitives.Bytes;
import org.gradle.api.internal.hash.DefaultHasher;
import org.gradle.api.internal.hash.Hasher;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.*;

public class HashClassPathSnapshotter implements ClassPathSnapshotter {

    private final Hasher hasher = new DefaultHasher();

    public ClassPathSnapshot snapshot(ClassPath classPath) {
        List<String> visitedFilePaths = new LinkedList<String>();
        Set<File> visitedDirs = new LinkedHashSet<File>();
        byte[] combinedHash = new byte[0];
        List<File> cpFiles = classPath.getAsFiles();
        combinedHash = hash(visitedFilePaths, visitedDirs, combinedHash, cpFiles.toArray(new File[cpFiles.size()]));
        return new ClassPathSnapshotImpl(visitedFilePaths, combinedHash);
    }

    private byte[] hash(List<String> visitedFilePaths, Set<File> visitedDirs, byte[] combinedHash, File[] toHash) {
        for (File file : toHash) {
            file = GFileUtils.canonicalise(file);
            if (file.isDirectory()) {
                if (visitedDirs.add(file)) {
                    //in theory, awkward symbolic links can lead to recursion problems.
                    //TODO - figure out a way to test it. I only tested it 'manually' and the feature is needed.
                    combinedHash = hash(visitedFilePaths, visitedDirs, combinedHash, file.listFiles());
                }
            } else if (file.isFile()) {
                visitedFilePaths.add(file.getAbsolutePath());
                //TODO SF Luke prefers keeping a list instead of merging arrays
                combinedHash = Bytes.concat(combinedHash, hasher.hash(file));
            }
            //else an empty folder - a legit situation
        }
        return combinedHash;
    }

    private class ClassPathSnapshotImpl implements ClassPathSnapshot {
        private final List<String> files;
        private final byte[] combinedHash;

        public ClassPathSnapshotImpl(List<String> files, byte[] combinedHash) {
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
            return this.files.equals(that.files) && Arrays.equals(this.combinedHash, that.combinedHash);
        }

        public int hashCode() {
            int result = files.hashCode();
            result = 31 * result + Arrays.hashCode(combinedHash);
            return result;
        }
    }
}