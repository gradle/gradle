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

package org.gradle.api.internal.file.collections;

import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.internal.PatternSetFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.services.FileSystems;

import java.io.File;

public class DefaultDirectoryFileTreeFactory implements DirectoryFileTreeFactory {
    private final PatternSetFactory patternSetFactory;
    private final FileSystem fileSystem;

    public DefaultDirectoryFileTreeFactory() {
        this.patternSetFactory = new PatternSetFactory() {
            @Override
            public PatternSet createPatternSet() {
                return new PatternSet();
            }
        };
        this.fileSystem = FileSystems.getDefault();
    }

    public DefaultDirectoryFileTreeFactory(PatternSetFactory patternSetFactory, FileSystem fileSystem) {
        this.patternSetFactory = patternSetFactory;
        this.fileSystem = fileSystem;
    }

    @Override
    public DirectoryFileTree create(File directory) {
        return new DirectoryFileTree(directory, patternSetFactory.createPatternSet(), fileSystem);
    }

    @Override
    public DirectoryFileTree create(File directory, PatternSet patternSet) {
        return new DirectoryFileTree(directory, patternSet, fileSystem);
    }
}
