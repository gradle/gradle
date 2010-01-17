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
import org.gradle.api.internal.project.ProjectInternal;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * @author Hans Dockter
 */
public class Tar extends AbstractArchiveTask {
    private final CopyActionImpl action;

    private Compression compression;

    private LongFile longFile;

    public Tar() {
        compression = Compression.NONE;
        longFile = LongFile.WARN;
        action = new TarCopyActionImpl(((ProjectInternal) getProject()).getFileResolver());
        getConventionMapping().map("extension", new Callable<Object>(){
            public Object call() throws Exception {
                return getCompression().getExtension();
            }
        }).noCache();
    }

    protected CopyActionImpl getCopyAction() {
        return action;
    }

    public Compression getCompression() {
        return compression;
    }

    public void setCompression(Compression compression) {
        this.compression = compression;
    }

    public LongFile getLongFile() {
        return longFile;
    }

    public void setLongFile(LongFile longFile) {
        this.longFile = longFile;
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
