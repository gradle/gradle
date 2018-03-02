/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.base.Objects;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class IncrementalCompileSourceProcessorCache {
    private final Map<File, IncrementalCompileFilesFactory.FileDetails> fileBasedCache = new HashMap<File, IncrementalCompileFilesFactory.FileDetails>();
    private final Map<Integer, IncrementalCompileFilesFactory.FileDetails> macroBasedCache = new HashMap<Integer, IncrementalCompileFilesFactory.FileDetails>();
    private final Object lock = new Object();

    public IncrementalCompileFilesFactory.FileDetails get(File file, Integer macroHash) {
        synchronized (lock) {
            IncrementalCompileFilesFactory.FileDetails result = fileBasedCache.get(file);
            if (result == null) {
                result = macroBasedCache.get(Objects.hashCode(file, macroHash));
            }
            return result;
        }
    }

    public void put(File file, IncrementalCompileFilesFactory.FileDetails fileDetails) {
        synchronized (lock) {
            fileBasedCache.put(file, fileDetails);
        }
    }

    public void put(File file, Integer macroHash, IncrementalCompileFilesFactory.FileDetails fileDetails) {
        synchronized (lock) {
            macroBasedCache.put(Objects.hashCode(file, macroHash), fileDetails);
        }
    }

    public void clear() {
        synchronized (lock) {
            fileBasedCache.clear();
            macroBasedCache.clear();
        }
    }
}
