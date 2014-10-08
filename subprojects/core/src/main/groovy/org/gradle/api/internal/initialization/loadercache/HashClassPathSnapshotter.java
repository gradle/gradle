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

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class HashClassPathSnapshotter implements ClassPathSnapshotter {

    private final Hasher hasher = new DefaultHasher();

    public ClassPathSnapshot snapshot(ClassPath classPath) {
        List<String> visitedFilePaths = new LinkedList<String>();
        byte[] combinedHash = new byte[0];
        List<File> cpFiles = classPath.getAsFiles();
        combinedHash = hash(visitedFilePaths, combinedHash, cpFiles.toArray(new File[cpFiles.size()]));
        return new ClassPathSnapshotImpl(visitedFilePaths, combinedHash);
    }

    private byte[] hash(List<String> visitedFilePaths, byte[] combinedHash, File[] toHash) {
        for (File file : toHash) {
            if (file.isDirectory()) {
                combinedHash = hash(visitedFilePaths, combinedHash, file.listFiles());
            } else {
                visitedFilePaths.add(file.getAbsolutePath());
                combinedHash = Bytes.concat(combinedHash, hasher.hash(file));
            }
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