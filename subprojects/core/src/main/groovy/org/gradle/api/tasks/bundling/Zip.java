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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.archive.ZipCopyAction;
import org.gradle.api.internal.file.archive.ZipCopySpecVisitor;
import org.gradle.api.internal.file.copy.CopyActionImpl;
import org.gradle.api.internal.file.copy.ZipCompressedCompressor;
import org.gradle.api.internal.file.copy.ZipCompressor;
import org.gradle.api.internal.file.copy.ZipDeflatedCompressor;

import java.io.File;

/**
 * Assembles a ZIP archive.
 * 
 * The default is to compress the contents of the zip.
 * 
 * @author Hans Dockter
 */
public class Zip extends AbstractArchiveTask {
    public static final String ZIP_EXTENSION = "zip";
    private final ZipCopyActionImpl action;
    private boolean compressed = true;

    public Zip() {
        setExtension(ZIP_EXTENSION);
        action = new ZipCopyActionImpl(getServices().get(FileResolver.class));
    }
    
    /**
     * Returns if the archive will be compressed.
     * 
     * @return whether the archive will be compressed.
     */
    public boolean getCompressed() {
        return compressed;
    }
    
    /**
     * Sets whether to compress the contents of the archive.
     * 
     * @param compressed
     */
    public void setCompressed(boolean compressed) {
        this.compressed = compressed;
    }

    protected ZipCopyActionImpl getCopyAction() {
        return action;
    }

    /**
     * Zip compress action implementation.
     */
    protected class ZipCopyActionImpl extends CopyActionImpl implements ZipCopyAction {
        public ZipCopyActionImpl(FileResolver fileResolver) {
            super(fileResolver, new ZipCopySpecVisitor());
        }

        public File getArchivePath() {
            return Zip.this.getArchivePath();
        }

        public ZipCompressor getCompressor() {
            return compressed ? ZipCompressedCompressor.INSTANCE : ZipDeflatedCompressor.INSTANCE;
        }
    }
}
