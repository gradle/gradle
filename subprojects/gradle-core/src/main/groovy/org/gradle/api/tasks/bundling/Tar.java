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

import org.gradle.api.internal.file.*;
import org.gradle.api.internal.file.archive.TarCopyAction;
import org.gradle.api.internal.file.archive.TarCopySpecVisitor;
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

    private Compression compression;

    public Tar() {
        compression = Compression.NONE;
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
     * Returns the compression to use for this archive. The default is {@link org.gradle.api.tasks.bundling.Compression#NONE}.
     * @return The compression. Never returns null.
     */
    public Compression getCompression() {
        return compression;
    }

    /**
     * Specifies the compression to use for this archive.
     * @param compression The compression. Should not be null.
     */
    public void setCompression(Compression compression) {
        this.compression = compression;
    }

    private class TarCopyActionImpl extends CopyActionImpl implements TarCopyAction {
        public TarCopyActionImpl(FileResolver fileResolver) {
            super(fileResolver, new TarCopySpecVisitor());
        }

        public File getArchivePath() {
            return Tar.this.getArchivePath();
        }

        public Compression getCompression() {
            return Tar.this.getCompression();
        }
    }
}
