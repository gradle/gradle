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

package org.gradle.api.internal.tasks.compile.incremental.deps;

import org.gradle.api.internal.tasks.compile.incremental.cache.LocalClassDependencyInfoStore;

public class LocalClassDependencyInfoCache implements ClassDependencyInfoProvider, ClassDependencyInfoWriter {
    private final LocalClassDependencyInfoStore dependencyInfoStore;

    public LocalClassDependencyInfoCache(LocalClassDependencyInfoStore dependencyInfoStore) {
        this.dependencyInfoStore = dependencyInfoStore;
    }

    public void writeInfo(ClassDependencyInfo info) {
        dependencyInfoStore.put(info);
    }

    public ClassDependencyInfo provideInfo() {
        return dependencyInfoStore.get();
    }

    public boolean isInfoAvailable() {
        //TODO SF get rid of this and then use the LocalClassDependencyInfoStore directly.
        return dependencyInfoStore.get() != null;
    }
}