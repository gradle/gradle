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

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

@SuppressWarnings("Since15")
public class DefaultBuildOutputCleanupRegistry implements BuildOutputCleanupRegistry {

    private final FileResolver fileResolver;
    private final Set<FileCollection> outputs = Sets.newHashSet();
    private Set<Path> resolvedPaths;

    public DefaultBuildOutputCleanupRegistry(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    @Override
    public synchronized void registerOutputs(Object files) {
        if (resolvedPaths != null) {
            resolvedPaths = null;
        }
        this.outputs.add(fileResolver.resolveFiles(files));
    }

    @Override
    public boolean isOutputOwnedByBuild(File file) {
        Set<Path> safeToDelete = getResolvedPaths();
        Path absolutePath = file.toPath().toAbsolutePath();
        while (absolutePath != null) {
            if (safeToDelete.contains(absolutePath)) {
                return true;
            }
            absolutePath = absolutePath.getParent();
        }
        return false;
    }

    private Set<Path> getResolvedPaths() {
        if (resolvedPaths == null) {
            doResolvePaths();
        }
        return resolvedPaths;
    }

    private synchronized void doResolvePaths() {
        if (resolvedPaths == null) {
            Set<Path> result = new LinkedHashSet<Path>();
            for (FileCollection output : outputs) {
                for (File file : output.getFiles()) {
                    result.add(file.toPath().toAbsolutePath());
                }
            }
            resolvedPaths = result;
        }
    }
}
