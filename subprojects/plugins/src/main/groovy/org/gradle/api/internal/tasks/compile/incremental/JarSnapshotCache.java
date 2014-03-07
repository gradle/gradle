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

import java.io.File;
import java.util.List;

public class JarSnapshotCache {

    private File classDeltaCache;

    public JarSnapshotCache(File projectJar) {
        this.classDeltaCache = new File(classDeltaCacheFile(projectJar));
    }

    private static String classDeltaCacheFile(File projectJar) {
        return projectJar + "-class-delta.bin";
    }

    public JarDelta jarChanged(final File inputFile) {
        final File classDelta = new File(classDeltaCacheFile(inputFile));

        return new JarDelta() {
            private List<String> changedClasses = (classDelta.isFile())? (List<String>) DummySerializer.readFrom(classDelta) : null;

            public boolean isFullRebuildNeeded() {
                return changedClasses == null;
            }

            public Iterable<String> getChangedClasses() {
                return changedClasses;
            }
        };
    }

    public void rememberDelta(List<String> changedSources) {
        classDeltaCache.getParentFile().mkdirs();
        DummySerializer.writeTargetTo(classDeltaCache, changedSources);
    }
}
