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
import org.gradle.api.internal.file.FileCollectionFactory;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultBuildOutputCleanupRegistry implements BuildOutputCleanupRegistry {

    private final FileCollectionFactory fileCollectionFactory;
    private final Set<FileCollection> outputs = Sets.newHashSet();
    private Set<String> resolvedPaths;

    public DefaultBuildOutputCleanupRegistry(FileCollectionFactory fileCollectionFactory) {
        this.fileCollectionFactory = fileCollectionFactory;
    }

    @Override
    public synchronized void registerOutputs(Object files) {
        if (resolvedPaths != null) {
            resolvedPaths = null;
        }
        this.outputs.add(fileCollectionFactory.resolving(files));
    }

    @Override
    public boolean isOutputOwnedByBuild(File file) {
        Set<String> safeToDelete = getResolvedPaths();
        File absoluteFile = file.getAbsoluteFile();
        while (absoluteFile != null) {
            if (safeToDelete.contains(absoluteFile.getPath())) {
                return true;
            }
            absoluteFile = absoluteFile.getParentFile();
        }
        return false;
    }

    @Override
    public Set<FileCollection> getRegisteredOutputs() {
        return outputs;
    }

    private Set<String> getResolvedPaths() {
        if (resolvedPaths == null) {
            doResolvePaths();
        }
        return resolvedPaths;
    }

    private synchronized void doResolvePaths() {
        if (resolvedPaths == null) {
            Set<String> result = new LinkedHashSet<String>();
            for (FileCollection output : outputs) {
                for (File file : output) {
                    result.add(file.getAbsolutePath());
                }
            }
            resolvedPaths = result;
        }
    }
}
