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
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;
import java.nio.charset.Charset;

public class FileCollectionBackedArchiveTextResource extends FileCollectionBackedTextResource {
    public FileCollectionBackedArchiveTextResource(final FileOperations fileOperations,
                                                   final TemporaryFileProvider tempFileProvider,
                                                   final FileCollection fileCollection,
                                                   final String path, Charset charset) {
        super(tempFileProvider, new LazilyInitializedFileCollection() {
            @Override
            public String getDisplayName() {
                return String.format("entry '%s' in archive %s", path, fileCollection);
            }

            @Override
            public FileCollection createDelegate() {
                File archiveFile = fileCollection.getSingleFile();
                String fileExtension = Files.getFileExtension(archiveFile.getName());
                FileTree archiveContents = fileExtension.equals("jar") || fileExtension.equals("zip")
                    ? fileOperations.zipTree(archiveFile) : fileOperations.tarTree(archiveFile);
                PatternSet patternSet = new PatternSet();
                patternSet.include(path);
                return archiveContents.matching(patternSet);
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                context.add(fileCollection);
            }
        }, charset);
    }
}
