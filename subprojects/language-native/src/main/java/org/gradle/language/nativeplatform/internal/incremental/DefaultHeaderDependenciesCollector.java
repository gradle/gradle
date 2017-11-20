/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@NonNullApi
public class DefaultHeaderDependenciesCollector implements HeaderDependenciesCollector {
    private final Logger logger = LoggerFactory.getLogger(DefaultHeaderDependenciesCollector.class);
    private final DirectoryFileTreeFactory directoryFileTreeFactory;

    public DefaultHeaderDependenciesCollector(DirectoryFileTreeFactory directoryFileTreeFactory) {
        this.directoryFileTreeFactory = directoryFileTreeFactory;
    }

    @Override
    public ImmutableSortedSet<File> collectHeaderDependencies(String taskPath, List<File> includeRoots, IncrementalCompilation incrementalCompilation) {
        final Set<File> headerDependencies = new HashSet<File>();
        if (incrementalCompilation.isUnresolvedHeaders()) {
            addIncludeRoots(taskPath, includeRoots, headerDependencies);
        } else {
            headerDependencies.addAll(incrementalCompilation.getDiscoveredInputs());
        }
        return ImmutableSortedSet.copyOf(headerDependencies);
    }

    @Override
    public ImmutableSortedSet<File> collectExistingHeaderDependencies(String taskPath, List<File> includeRoots, IncrementalCompilation incrementalCompilation) {
        final Set<File> headerDependencies = new HashSet<File>();
        if (incrementalCompilation.isUnresolvedHeaders()) {
            addIncludeRoots(taskPath, includeRoots, headerDependencies);
        } else {
            headerDependencies.addAll(incrementalCompilation.getExistingHeaders());
        }
        return ImmutableSortedSet.copyOf(headerDependencies);
    }

    private void addIncludeRoots(String taskPath, List<File> includeRoots, final Set<File> headerDependencies) {
        logger.info("After parsing the source files, Gradle cannot calculate the exact set of include files for '{}'. Every file in the include search path will be considered a header dependency.", taskPath);
        for (final File includeRoot : includeRoots) {
            logger.info("adding files in {} to header dependencies for {}", includeRoot, taskPath);
            directoryFileTreeFactory.create(includeRoot).visit(new EmptyFileVisitor() {
                @Override
                public void visitFile(FileVisitDetails fileDetails) {
                    headerDependencies.add(fileDetails.getFile());
                }
            });
        }
    }
}
