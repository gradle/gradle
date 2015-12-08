/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.file;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.collections.*;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class FileTrees {
    public static void visitTreeOrBackingFile(FileCollection input, FileVisitor visitor) {
        DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext();
        context.add(input);
        List<FileTreeInternal> fileTrees = context.resolveAsFileTrees();

        for (FileTreeInternal fileTree : fileTrees) {
            Set<File> fileTreeBackingFiles = unwrapFileTreeBackingFilesIfAvailable(fileTree);
            if (fileTreeBackingFiles != null) {
                for (File fileTreeSourceFile : fileTreeBackingFiles) {
                    visitor.visitFile(new DefaultFileVisitDetails(fileTreeSourceFile));
                }
            } else {
                fileTree.visit(visitor);
            }
        }
    }

    private static Set<File> unwrapFileTreeBackingFilesIfAvailable(Object fileTree) {
        if (fileTree instanceof FileTreeWithBackingFile) {
            File backingFile = ((FileTreeWithBackingFile) fileTree).getBackingFile();
            if (backingFile != null) {
                return Collections.singleton(backingFile);
            }
            if (fileTree instanceof FileSystemMirroringFileTree) {
                // custom resource as source for TarFileTree, fallback to snapshotting files in archive
                return new FileTreeAdapter((FileSystemMirroringFileTree) fileTree).getFiles();
            }
        } else if (fileTree instanceof FileTreeAdapter) {
            return unwrapFileTreeBackingFilesIfAvailable(((FileTreeAdapter) fileTree).getTree());
        } else if (fileTree instanceof FilteredFileTree) {
            return unwrapFileTreeBackingFilesIfAvailable(((FilteredFileTree) fileTree).getOriginalFileTree());
        }
        return null;
    }
}
