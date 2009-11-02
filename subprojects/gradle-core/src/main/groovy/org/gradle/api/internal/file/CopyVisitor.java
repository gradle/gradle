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
package org.gradle.api.internal.file;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.CopyAction;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;

import java.io.File;

/**
 * @author Steve Appling
 */
public class CopyVisitor implements CopySpecVisitor {
    private File baseDestDir;
    private boolean didWork;

    public void startVisit(CopyAction action) {
    }

    public void endVisit() {
    }

    public void visitSpec(CopySpecImpl spec) {
        baseDestDir = spec.getDestDir();
        if (baseDestDir == null) {
            throw new InvalidUserDataException("No copy destination directory has been specified, use 'into' to specify a target directory.");
        }
    }

    public void visitDir(FileVisitDetails dirDetails) {
    }

    public void visitFile(FileVisitDetails source) {
        File target = source.getRelativePath().getFile(baseDestDir);
        copyFile(source, target);
    }

    public boolean getDidWork() {
        return didWork;
    }

    void copyFile(FileTreeElement srcFile, File destFile) {
        boolean copied = srcFile.copyTo(destFile);
        if (copied) {
            didWork = true;
        }
    }
}
