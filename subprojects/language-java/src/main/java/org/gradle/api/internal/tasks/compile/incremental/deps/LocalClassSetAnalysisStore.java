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

package org.gradle.api.internal.tasks.compile.incremental.deps;

import org.gradle.api.internal.cache.Loader;
import org.gradle.api.internal.cache.Stash;
import org.gradle.cache.PersistentIndexedCache;

//Keeps the class set analysis of the given JavaCompile task
public class LocalClassSetAnalysisStore implements Loader<ClassSetAnalysisData>, Stash<ClassSetAnalysisData> {
    private final String taskPath;
    private final PersistentIndexedCache<String, ClassSetAnalysisData> cache;

    public LocalClassSetAnalysisStore(String taskPath, PersistentIndexedCache<String, ClassSetAnalysisData> cache) {
        this.taskPath = taskPath;
        this.cache = cache;
    }

    @Override
    public void put(ClassSetAnalysisData analysis) {
        cache.put(taskPath, analysis);
    }

    @Override
    public ClassSetAnalysisData get() {
        return cache.get(taskPath);
    }
}
