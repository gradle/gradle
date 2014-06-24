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

package org.gradle.api.internal.tasks.compile.incremental.jar;

import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfo;

import java.io.Serializable;
import java.util.Map;

public class JarSnapshotData implements Serializable { //TODO SF hand-craft serialization

    final Map<String, byte[]> hashes;
    final ClassDependencyInfo info;
    final byte[] hash;

    /**
     * @param hash of this jar
     * @param hashes hashes of all classes from the jar
     * @param info dependency info of classes in this jar
     */
    public JarSnapshotData(byte[] hash, Map<String, byte[]> hashes, ClassDependencyInfo info) {
        assert hash != null;
        assert hashes != null;
        assert info != null;

        this.hash = hash;
        this.hashes = hashes;
        this.info = info;
    }
}