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

package org.gradle.api.internal.tasks.compile.incremental.classpath;

import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.internal.hash.HashCode;

import java.util.Map;

public class ClasspathEntrySnapshotData {

    private final Map<String, HashCode> hashes;
    private final ClassSetAnalysisData classAnalysis;
    private final HashCode hash;

    /**
     * @param hash of this entry
     * @param hashes hashes of all classes from the entry
     * @param classAnalysis of classes analysis in this entry
     */
    public ClasspathEntrySnapshotData(HashCode hash, Map<String, HashCode> hashes, ClassSetAnalysisData classAnalysis) {
        assert hash != null;
        assert hashes != null;
        assert classAnalysis != null;

        this.hash = hash;
        this.hashes = hashes;
        this.classAnalysis = classAnalysis;
    }

    public Map<String, HashCode> getHashes() {
        return hashes;
    }

    public ClassSetAnalysisData getClassAnalysis() {
        return classAnalysis;
    }

    public HashCode getHash() {
        return hash;
    }
}
