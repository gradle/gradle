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

import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;

import java.io.*;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileToArchiveEntrySetTransformer implements Transformer<Set<ArchiveEntry>, File> {

    public Set<ArchiveEntry> transform(File archiveFile) {
        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(archiveFile);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }

        return transform(fileInputStream, null, null);
    }

    private ImmutableSet<ArchiveEntry> transform(InputStream archiveInputStream, String sortPathPrefix, String pathPrefix) {
        ImmutableSet.Builder<ArchiveEntry> entries = ImmutableSet.builder();
        ZipInputStream zipStream = new ZipInputStream(archiveInputStream);

        try {
            ZipEntry entry = zipStream.getNextEntry();
            while (entry != null) {
                ArchiveEntry.Builder builder = new ArchiveEntry.Builder();
                builder.setPath(entry.getName());
                builder.setCrc(entry.getCrc());
                builder.setDirectory(entry.isDirectory());
                builder.setSize(entry.getSize());
                if (sortPathPrefix == null) {
                    builder.setSortPath(builder.getPath());
                } else {
                    builder.setSortPath(sortPathPrefix + builder.getPath());
                }
                if (pathPrefix != null) {
                    builder.setPath(pathPrefix + builder.getPath());
                }
                if (!builder.isDirectory() && (zipStream.available() == 1)) {
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
                        ImmutableSet<ArchiveEntry> subEntries = transform(bis, builder.getSortPath() + "::", "jar:" + builder.getPath() + "!/");
                        builder.setSubEntries(subEntries);
                    }
                }

                ArchiveEntry archiveEntry = builder.build();
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

        return entries.build();
    }

}
