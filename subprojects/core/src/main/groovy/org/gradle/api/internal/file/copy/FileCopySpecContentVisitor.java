/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.copy;

import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.file.FileResolver;

import java.io.File;

/**
 * @author Steve Appling
 */
public class FileCopySpecContentVisitor extends EmptyCopySpecContentVisitor {
    private final FileResolver fileResolver;
    private boolean didWork;

    public FileCopySpecContentVisitor(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    public void visitFile(FileCopyDetails source) {
        visitFileOrDir(source);
    }

    public void visitDir(FileCopyDetails source) {
        visitFileOrDir(source);
    }

    public boolean getDidWork() {
        return didWork;
    }

    private void visitFileOrDir(FileTreeElement source) {
        File target = fileResolver.resolve(source.getRelativePath().getPathString());
        copyFile(source, target);
    }

    private void copyFile(FileTreeElement srcFile, File destFile) {
        boolean copied = srcFile.copyTo(destFile);
        if (copied) {
            didWork = true;
        }
    }
}
