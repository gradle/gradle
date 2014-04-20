/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.buildcomparison.outcome.internal.archive.entry;

import org.apache.commons.io.IOUtils;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileToArchiveEntrySetTransformer implements Transformer<Set<ArchiveEntry>, File> {

    private final Transformer<ArchiveEntry, ZipEntry> entryTransformer;

    public FileToArchiveEntrySetTransformer(Transformer<ArchiveEntry, ZipEntry> entryTransformer) {
        this.entryTransformer = entryTransformer;
    }

    public Set<ArchiveEntry> transform(File archiveFile) {
        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(archiveFile);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }

        return transform(fileInputStream, null, null);
    }

    private Set<ArchiveEntry> transform(InputStream archiveInputStream, String sortPathPrefix, String pathPrefix) {
        Set<ArchiveEntry> entries = new HashSet<ArchiveEntry>();

        ZipInputStream zipStream = new ZipInputStream(archiveInputStream);

        try {
            ZipEntry entry = zipStream.getNextEntry();
            while (entry != null) {
                ArchiveEntry archiveEntry = entryTransformer.transform(entry);
                if (sortPathPrefix == null) {
                    archiveEntry.setSortPath(archiveEntry.getPath());
                } else {
                    archiveEntry.setSortPath(sortPathPrefix + archiveEntry.getPath());
                }
                if (pathPrefix != null) {
                    archiveEntry.setPath(pathPrefix + archiveEntry.getPath());
                }
                if (!archiveEntry.isDirectory() && (zipStream.available() == 1)) {
                    boolean zipEntry;
                    final BufferedInputStream bis = new BufferedInputStream(zipStream) {
                        @Override
                        public void close() throws IOException {
                        }
                    };
                    try {
                        bis.mark(Integer.MAX_VALUE);
                        zipEntry = new ZipInputStream(bis).getNextEntry() != null;
                    } catch (IOException e) {
                        zipEntry = false;
                    } finally {
                        bis.reset();
                    }
                    if (zipEntry) {
                        Set<ArchiveEntry> subEntries = transform(bis, archiveEntry.getSortPath() + "::", "jar:" + archiveEntry.getPath() + "!/");
                        archiveEntry.setSubEntries(subEntries);
                    }
                }
                entries.add(archiveEntry);
                entries.addAll(archiveEntry.getSubEntries());
                zipStream.closeEntry();
                entry = zipStream.getNextEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            IOUtils.closeQuietly(zipStream);
        }

        return entries;
    }

}
