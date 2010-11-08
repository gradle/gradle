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

import org.apache.tools.zip.UnixStat;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;

import java.io.File;

/**
 * @author Steve Appling
 */
public class FileCopySpecVisitor extends EmptyCopySpecVisitor {

    private File baseDestDir;
    private boolean didWork;
    private ReadableCopySpec spec;

    public void startVisit(CopyAction action) {
        baseDestDir = ((FileCopyAction) action).getDestinationDir();
        if (baseDestDir == null) {
            throw new InvalidUserDataException("No copy destination directory has been specified, use 'into' to specify a target directory.");
        }
    }

    public void visitSpec(ReadableCopySpec spec) {
        this.spec = spec;
    }

    public void visitFile(FileVisitDetails source) {
        File target = source.getRelativePath().getFile(baseDestDir);
        copyFile(source, target);
        chmod(target);
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

    void chmod(File destFile) {
        int fileMode = spec.getFileMode();
        if (fileMode != UnixStat.DEFAULT_FILE_PERM) {  // if we have default file mode do nothing, otherwise chmod(..)  destFile 
            FileChmod fileChmod = getFileChmod();
            fileChmod.chmod(destFile, spec.getFileMode());
        }
    }

    FileChmod getFileChmod() {
        return new AntBasedFileChmod();
    }
}
