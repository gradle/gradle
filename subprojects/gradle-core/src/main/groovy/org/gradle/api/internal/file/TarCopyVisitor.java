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

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.CopyAction;
import org.gradle.api.file.FileVisitDetails;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class TarCopyVisitor implements CopySpecVisitor {
    private TarArchiveOutputStream tarOutStr;
    private File tarFile;

    public void startVisit(CopyAction action) {
        ArchiveCopyAction archiveAction = (ArchiveCopyAction) action;
        try {
            tarFile = archiveAction.getArchivePath();
            tarOutStr = new TarArchiveOutputStream(new FileOutputStream(tarFile));
            tarOutStr.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        } catch (Exception e) {
            throw new GradleException(String.format("Could not create TAR '%s'.", tarFile), e);
        }
    }

    public void endVisit() {
        try {
            tarOutStr.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitSpec(CopySpecImpl spec) {
    }

    public void visitFile(FileVisitDetails fileDetails) {
        try {
            TarArchiveEntry archiveEntry = new TarArchiveEntry(fileDetails.getRelativePath().getPathString());
            archiveEntry.setModTime(fileDetails.getLastModified());
            archiveEntry.setSize(fileDetails.getSize());
            tarOutStr.putArchiveEntry(archiveEntry);
            fileDetails.copyTo(tarOutStr);
            tarOutStr.closeArchiveEntry();
        } catch (Exception e) {
            throw new GradleException(String.format("Could not add %s to TAR '%s'.", fileDetails, tarFile), e);
        }
    }

    public void visitDir(FileVisitDetails dirDetails) {
    }

    public boolean getDidWork() {
        return true;
    }
}