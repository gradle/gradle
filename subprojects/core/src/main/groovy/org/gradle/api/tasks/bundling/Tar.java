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
import org.gradle.api.internal.file.archive.TarCopyAction;
import org.gradle.api.internal.file.archive.TarCopySpecVisitor;
import org.gradle.api.internal.file.archive.compression.ArchiverFactory;
import org.gradle.api.internal.file.copy.CopyActionImpl;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * Assembles a TAR archive.
 *
 * @author Hans Dockter
 */
public class Tar extends AbstractArchiveTask {
    private final CopyActionImpl action;
    private Compressor compressor;

    public Tar() {
        setCompression(Compression.NONE);
        action = new TarCopyActionImpl(getServices().get(FileResolver.class));
        getConventionMapping().map("extension", new Callable<Object>(){
            public Object call() throws Exception {
                return getCompression().getExtension();
            }
        });
    }

    protected CopyActionImpl getCopyAction() {
        return action;
    }

    /**
     * Returns the compressor that is used in this tar task.
     *
     * @return The compressor. Never returns null.
     */
    public Compressor getCompressor() {
        return compressor;
    }

    /**
     * Specifies the compressor for this tar task
     *
     * @param compressor compressor
     */
    public void setCompressor(Compressor compressor) {
        assert compressor != null : "compressor cannot be null";
        this.compressor = compressor;
    }

    /**
     * Returns the compression that is used for this archive.
     *
     * @return The compression. Never returns null.
     */
    public Compression getCompression() {
        if (compressor instanceof CompressionAware) {
            return ((CompressionAware) compressor).getCompression();
        }
        return Compression.NONE;
    }

    /**
     * Configures the compressor based on passed in compression.
     *
     * @param compression The compression. Should not be null.
     */
    public void setCompression(Compression compression) {
        compressor = new ArchiverFactory().archiver(compression);
    }

    private class TarCopyActionImpl extends CopyActionImpl implements TarCopyAction {
        public TarCopyActionImpl(FileResolver fileResolver) {
            super(fileResolver, new TarCopySpecVisitor());
        }

        public File getArchivePath() {
            return Tar.this.getArchivePath();
        }

        public Compressor getCompressor() {
            return Tar.this.getCompressor();
        }
    }
}
