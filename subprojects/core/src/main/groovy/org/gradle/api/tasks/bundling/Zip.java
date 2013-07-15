/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.tasks.bundling;

import org.gradle.api.internal.file.archive.ZipCopySpecContentVisitor;
import org.gradle.api.internal.file.copy.CopySpecContentVisitor;
import org.gradle.api.internal.file.copy.ZipCompressor;
import org.gradle.api.internal.file.copy.ZipDeflatedCompressor;
import org.gradle.api.internal.file.copy.ZipStoredCompressor;

/**
 * Assembles a ZIP archive.
 * 
 * The default is to compress the contents of the zip.
 * 
 * @author Hans Dockter
 */
public class Zip extends AbstractArchiveTask {
    public static final String ZIP_EXTENSION = "zip";
    private ZipEntryCompression entryCompression = ZipEntryCompression.DEFLATED;

    public Zip() {
        setExtension(ZIP_EXTENSION);
    }

    protected ZipCompressor getCompressor() {
        switch(entryCompression) {
            case DEFLATED:
                return ZipDeflatedCompressor.INSTANCE;
            case STORED:
                return ZipStoredCompressor.INSTANCE;
            default:
                throw new IllegalArgumentException(String.format("Unknown Compression type %s", entryCompression));
        }
    }

    @Override
    protected CopySpecContentVisitor createContentVisitor() {
        return new ZipCopySpecContentVisitor(getArchivePath(), getCompressor());
    }

    /**
     * Returns the compression level of the entries of the archive. If set to {@link ZipEntryCompression#DEFLATED} (the default), each entry is
     * compressed using the DEFLATE algorithm. If set to {@link ZipEntryCompression#STORED} the entries of the archive are left uncompressed.
     *
     * @return the compression level of the archive contents.
     */
    public ZipEntryCompression getEntryCompression() {
        return entryCompression;
    }
    
    /**
     * Sets the compression level of the entries of the archive. If set to {@link ZipEntryCompression#DEFLATED} (the default), each entry is
     * compressed using the DEFLATE algorithm. If set to {@link ZipEntryCompression#STORED} the entries of the archive are left uncompressed.
     *
     * @param entryCompression {@code STORED} or {@code DEFLATED}
     */
    public void setEntryCompression(ZipEntryCompression entryCompression) {
        this.entryCompression = entryCompression;
    }

}
