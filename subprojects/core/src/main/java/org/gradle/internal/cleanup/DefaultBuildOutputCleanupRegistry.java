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

package org.gradle.internal.cleanup;

import com.google.common.collect.Sets;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.UnionFileCollection;

import java.util.Set;

public class DefaultBuildOutputCleanupRegistry implements BuildOutputCleanupRegistry {

    private final FileResolver fileResolver;
    private final Set<FileCollection> outputs = Sets.newHashSet();

    public DefaultBuildOutputCleanupRegistry(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    @Override
    public void registerOutputs(Object files) {
        this.outputs.add(fileResolver.resolveFiles(files));
    }

    @Override
    public FileCollection getOutputs() {
        return new UnionFileCollection(outputs);
    }
}
