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
package org.gradle.language.nativeplatform.internal.incremental;

import java.io.File;
import java.util.List;

public class DefaultIncrementalCompilation implements IncrementalCompilation {
    private final List<File> recompile;
    private final List<File> removed;

    public DefaultIncrementalCompilation(List<File> recompile, List<File> removed) {
        this.recompile = recompile;
        this.removed = removed;
    }

    public List<File> getRecompile() {
        return recompile;
    }

    public List<File> getRemoved() {
        return removed;
    }
}
