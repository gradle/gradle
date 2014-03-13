/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental;

import java.io.Serializable;
import java.util.*;

class JarSnapshot implements Serializable {

    final Map<String, byte[]> classHashes;

    JarSnapshot(Map<String, byte[]> classHashes) {
        this.classHashes = classHashes;
    }

    JarDelta compareToSnapshot(JarSnapshot other) {
        final List<String> changedClasses = new LinkedList<String>();
        for (String thisCls : classHashes.keySet()) {
            byte[] hash = other.classHashes.get(thisCls);
            if (hash == null || !Arrays.equals(hash, classHashes.get(thisCls))) {
                changedClasses.add(thisCls);
            }
        }
        return new JarDelta() {
            public Collection<String> getChangedClasses() {
                return changedClasses;
            }
        };
    }
}