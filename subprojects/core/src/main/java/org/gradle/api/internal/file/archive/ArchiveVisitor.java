/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.file.archive;

import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.LinksStrategy;
import org.gradle.internal.file.Chmod;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.gradle.internal.file.PathTraversalChecker.safePathName;

public abstract class ArchiveVisitor<ENTRY> {
    private final File originalFile;
    protected final File expandedDir;
    private final FileVisitor visitor;
    protected final AtomicBoolean stopFlag;
    private final LinksStrategy linksStrategy;
    protected final Chmod chmod;
    protected TreeMap<String, ENTRY> entries = null;

    ArchiveVisitor(
        File originalFile,
        File expandedDir,
        FileVisitor visitor,
        AtomicBoolean stopFlag,
        Chmod chmod
    ) {
        this.originalFile = originalFile;
        this.expandedDir = expandedDir;
        this.visitor = visitor;
        this.stopFlag = stopFlag;
        this.linksStrategy = visitor.linksStrategy();
        this.chmod = chmod;
    }

    abstract TreeMap<String, ENTRY> getEntries();

    abstract boolean isSymlink(ENTRY entry);

    abstract boolean isDirectory(ENTRY entry);

    abstract String getPath(ENTRY entry);

    abstract String getSymlinkTarget(ENTRY entry);

    abstract int getUnixMode(ENTRY entry);

    abstract long getLastModifiedTime(ENTRY entry);

    abstract long getSize(ENTRY entry);

    abstract @Nullable ENTRY getEntry(String path);

    abstract AbstractArchiveFileTreeElement<ENTRY, ? extends ArchiveVisitor<ENTRY>> createDetails(
        ENTRY entry,
        String targetPath,
        @Nullable ArchiveSymbolicLinkDetails<ENTRY> linkDetails,
        boolean preserveLink
    );

    public void visitAll() throws IOException {
        Iterator<ENTRY> it = getEntries().values().iterator();
        while (it.hasNext() && !stopFlag.get()) {
            ENTRY entry = it.next();
            visitEntry(entry, safePathName(getPath(entry)), false);
        }
    }

    @Nullable
    ENTRY getTargetEntry(ENTRY entry) {
        String path = getPath(entry);
        ArrayList<String> parts = new ArrayList<>(Arrays.asList(path.split("/")));
        if (getTargetFollowingLinks(entry, parts, entry)) {
            String targetPath = String.join("/", parts);
            ENTRY targetEntry = getEntry(targetPath);
            if (targetEntry == null) { //retry for directories
                targetEntry = getEntry(targetPath + "/");
            }
            return targetEntry;
        } else {
            return null;
        }
    }

    private boolean getTargetFollowingLinks(ENTRY entry, ArrayList<String> parts, ENTRY originalEntry) {
        parts.remove(parts.size() - 1);
        String target = getSymlinkTarget(entry);
        for (String targetPart : target.split("/")) {
            if (targetPart.equals("..")) {
                if (parts.isEmpty()) {
                    return false;
                }
                parts.remove(parts.size() - 1);
            } else if (targetPart.equals(".")) {
                continue;
            } else {
                parts.add(targetPart);
                String currentPath = String.join("/", parts);
                ENTRY currentEntry = getEntry(currentPath);
                if (currentEntry != null && isSymlink(currentEntry)) {
                    if (currentEntry.equals(originalEntry)) {
                        return false; //cycle
                    }
                    boolean success = getTargetFollowingLinks(currentEntry, parts, originalEntry);
                    if (!success) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    protected void visitEntry(ENTRY entry, String targetPath, boolean extract) {
        if (!isSymlink(entry)) {
            FileVisitDetails details = createDetails(entry, targetPath, null, false);
            if (isDirectory(entry)) {
                visitor.visitDir(details);
            } else {
                if (extract) {
                    details.getFile();
                }
                visitor.visitFile(details);
            }
        } else {
            ArchiveSymbolicLinkDetails<ENTRY> linkDetails = new ArchiveSymbolicLinkDetails<>(entry, this);
            boolean preserveLink = linksStrategy.shouldBePreserved(linkDetails);
            FileVisitDetails details = createDetails(entry, targetPath, linkDetails, preserveLink);
            if (details.isDirectory()) {
                visitor.visitDir(details);
                ENTRY targetEntry = linkDetails.getTargetEntry();
                String originalPath = getPath(targetEntry);
                visitRecursively(originalPath, targetPath + '/');
            } else {
                visitor.visitFile(details);
            }
        }
    }

    private void visitRecursively(String originalPath, String targetPath) {
        String currentKey = originalPath;
        while (!stopFlag.get()) {
            Map.Entry<String, ENTRY> subEntry = getEntries().higherEntry(currentKey);
            if (subEntry != null && subEntry.getKey().startsWith(originalPath)) {
                currentKey = subEntry.getKey();
            } else {
                break;
            }

            String newPath = targetPath + currentKey.substring(originalPath.length());
            visitEntry(subEntry.getValue(), newPath, false);
        }
    }

    public File getOriginalFile() {
        return originalFile;
    }
}
