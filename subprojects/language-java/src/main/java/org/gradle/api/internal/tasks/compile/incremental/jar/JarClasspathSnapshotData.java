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

import com.google.common.hash.HashCode;

import java.io.File;
import java.util.Map;
import java.util.Set;

public class JarClasspathSnapshotData {

    private final Map<File, HashCode> jarHashes;
    private final Set<String> duplicateClasses;

    public JarClasspathSnapshotData(Map<File, HashCode> jarHashes, Set<String> duplicateClasses) {
        this.jarHashes = jarHashes;
        this.duplicateClasses = duplicateClasses;
    }

    public Set<String> getDuplicateClasses() {
        return duplicateClasses;
    }

    public Map<File, HashCode> getJarHashes() {
        return jarHashes;
    }
}
