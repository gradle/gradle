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

public class JarDeltaProvider {

    private List<String> changedSource;

    public JarDeltaProvider(File jarFile) {
        File classDelta = new File(jarFile + "-class-delta.bin");
        if (classDelta.isFile()) {
            changedSource = (List<String>) DummySerializer.readFrom(classDelta);
        }
    }

    public boolean isRebuildNeeded() {
        return changedSource == null;
    }

    public Iterable<String> getChangedClasses() {
        return changedSource;
    }
}
