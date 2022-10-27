/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.transaction;

import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.file.Deleter;

import java.util.List;
import java.util.Map;

public class JavaCompileTransaction extends AbstractCompileTransaction {
    public JavaCompileTransaction(
        JavaCompileSpec spec,
        PatternSet classesToDelete,
        Map<GeneratedResource.Location, PatternSet> resourcesToDelete,
        FileOperations fileOperations,
        Deleter deleter
    ) {
        super(spec, classesToDelete, resourcesToDelete, fileOperations, deleter);
    }

    @Override
    void doStashFiles(List<StashedFile> stashedFiles) {
        stashedFiles.forEach(StashedFile::stash);
    }
}
