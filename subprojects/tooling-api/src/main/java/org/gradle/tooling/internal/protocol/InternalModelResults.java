/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.protocol;

import com.google.common.collect.Lists;
import org.gradle.tooling.model.ModelResults;

import java.io.File;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;

/**
 * The internal protocol for transferring {@link ModelResults}
 */
public class InternalModelResults<T> implements Iterable<InternalModelResult<T>>, Serializable {
    private final List<InternalModelResult<T>> results = Lists.newArrayList();

    public void addBuildModel(File rootDir, T model) {
        results.add(new InternalModelResult<T>(rootDir, null, model, null));
    }

    public void addProjectModel(File rootDir, String projectPath, T model) {
        results.add(new InternalModelResult<T>(rootDir, projectPath, model, null));
    }

    public void addBuildFailure(File rootDir, RuntimeException failure) {
        results.add(new InternalModelResult<T>(rootDir, null, null, failure));
    }

    public void addProjectFailure(File rootDir, String projectPath, RuntimeException failure) {
        results.add(new InternalModelResult<T>(rootDir, projectPath, null, failure));
    }

    @Override
    public Iterator<InternalModelResult<T>> iterator() {
        return results.iterator();
    }
}
