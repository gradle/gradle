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

import com.google.common.hash.HashCode;
import org.gradle.internal.classpath.ClassPath;

/**
 * Creates snapshot based on file paths.
 */
public class FileClassPathSnapshotter implements ClassPathSnapshotter {
    public ClassPathSnapshot snapshot(final ClassPath classPath) {
        return new ClassPathSnapshotImpl(classPath);
    }

    private class ClassPathSnapshotImpl implements ClassPathSnapshot {
        private final ClassPath classPath;
        public ClassPathSnapshotImpl(ClassPath classPath) {
            assert classPath != null;
            this.classPath = classPath;
        }
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ClassPathSnapshotImpl)) {
                return false;
            }

            ClassPathSnapshotImpl that = (ClassPathSnapshotImpl) o;
            return classPath.equals(that.classPath);
        }
        public int hashCode() {
            return classPath.hashCode();
        }

        @Override
        public HashCode getStrongHash() {
            return HashCode.fromLong(hashCode());
        }
    }
}
