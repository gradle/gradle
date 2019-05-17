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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.IoActions;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileToArchiveEntrySetTransformer implements Transformer<Set<ArchiveEntry>, File> {

    @Override
    public Set<ArchiveEntry> transform(File archiveFile) {
        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(archiveFile);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }

        ImmutableSet.Builder<ArchiveEntry> allEntries = ImmutableSet.builder();
        walk(fileInputStream, allEntries, ImmutableList.<String>of());
        return allEntries.build();
    }

    private ImmutableSet<ArchiveEntry> walk(InputStream archiveInputStream, ImmutableSet.Builder<ArchiveEntry> allEntries, ImmutableList<String> parentPaths) {
        ImmutableSet.Builder<ArchiveEntry> entries = ImmutableSet.builder();
        ZipInputStream zipStream = new ZipInputStream(archiveInputStream);

        try {
            ZipEntry entry = zipStream.getNextEntry();
            while (entry != null) {
                ArchiveEntry.Builder builder = new ArchiveEntry.Builder();
                builder.setParentPaths(parentPaths);
                builder.setPath(entry.getName());
                builder.setCrc(entry.getCrc());
                builder.setDirectory(entry.isDirectory());
                builder.setSize(entry.getSize());
                if (!builder.isDirectory() && (zipStream.available() == 1)) {
                    boolean zipEntry;
                    final BufferedInputStream bis = new BufferedInputStream(zipStream) {
                        @Override
                        public void close() throws IOException {
                        }
                    };
                    bis.mark(Integer.MAX_VALUE);
                    zipEntry = new ZipInputStream(bis).getNextEntry() != null;
                    bis.reset();
                    if (zipEntry) {
                        ImmutableList<String> nextParentPaths = ImmutableList.<String>builder().addAll(parentPaths).add(entry.getName()).build();
                        ImmutableSet<ArchiveEntry> subEntries = walk(bis, allEntries, nextParentPaths);
                        builder.setSubEntries(subEntries);
                    }
                }

                ArchiveEntry archiveEntry = builder.build();
                entries.add(archiveEntry);
                allEntries.add(archiveEntry);
                zipStream.closeEntry();
                entry = zipStream.getNextEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            IoActions.closeQuietly(zipStream);
        }

        return entries.build();
    }

}
