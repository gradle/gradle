/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.file.collections;

import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.file.DefaultFileVisitDetails;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.services.FileSystems;

import java.io.File;

public class DefaultSingletonFileTree extends AbstractSingletonFileTree {
    private final File file;
    private final FileSystem fileSystem = FileSystems.getDefault();

    public DefaultSingletonFileTree(File file) {
        this(file, new PatternSet());
    }

    public DefaultSingletonFileTree(File file, PatternSet patternSet) {
        super(patternSet);
        this.file = file;
    }

    @Override
    public String getDisplayName() {
        return String.format("file '%s'", file);
    }

    @Override
    protected FileVisitDetails createFileVisitDetails() {
        return new DefaultFileVisitDetails(file, fileSystem, fileSystem);
    }

    @Override
    public void registerWatchPoints(FileSystemSubset.Builder builder) {
        builder.add(file);
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public MinimalFileTree filter(PatternFilterable patterns) {
        return new DefaultSingletonFileTree(file, filterPatternSet(patterns));
    }
}
