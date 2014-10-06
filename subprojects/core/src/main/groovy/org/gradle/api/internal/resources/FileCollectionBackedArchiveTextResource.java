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
package org.gradle.api.internal.resources;

import com.google.common.io.Files;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.collections.LazilyInitializedFileTree;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;

public class FileCollectionBackedArchiveTextResource implements TextResource {
    private final Charset charset;
    private final FileTree archiveTree;

    public FileCollectionBackedArchiveTextResource(final FileOperations fileOperations,
                                                   final FileCollection fileCollection,
                                                   final String path, Charset charset) {
        this.charset = charset;

        archiveTree = new LazilyInitializedFileTree() {
            @Override
            public FileTree createDelegate() {
                File archiveFile = fileCollection.getSingleFile();
                String fileExtension = Files.getFileExtension(archiveFile.getName());
                FileTree archiveContents = fileExtension.equals("jar") || fileExtension.equals("zip")
                        ? fileOperations.zipTree(archiveFile) : fileOperations.tarTree(archiveFile);
                PatternSet patternSet = new PatternSet();
                patternSet.include(path);
                return archiveContents.matching(patternSet);
            }
            public TaskDependency getBuildDependencies() {
                return fileCollection.getBuildDependencies();
            }
        };
    }

    public String asString() {
        try {
            return Files.toString(asFile(), charset);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Reader asReader() {
        try {
            return Files.newReader(asFile(), charset);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }

    public File asFile() {
        return archiveTree.getSingleFile();
    }

    public TaskDependency getBuildDependencies() {
        return archiveTree.getBuildDependencies();
    }

    public Object getInputProperties() {
        return charset.name();
    }

    public Object getInputFiles() {
        return archiveTree;
    }
}
