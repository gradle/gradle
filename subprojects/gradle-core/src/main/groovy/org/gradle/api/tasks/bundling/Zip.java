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
import org.gradle.api.internal.file.archive.ZipCopySpecVisitor;
import org.gradle.api.internal.file.copy.ArchiveCopyAction;
import org.gradle.api.internal.file.copy.CopyActionImpl;

import java.io.File;

/**
 * Assembles a ZIP archive.
 * 
 * @author Hans Dockter
 */
public class Zip extends AbstractArchiveTask {
    public static final String ZIP_EXTENSION = "zip";
    private final CopyActionImpl action;

    public Zip() {
        setExtension(ZIP_EXTENSION);
        action = new ZipCopyAction(getServices().get(FileResolver.class));
    }

    protected CopyActionImpl getCopyAction() {
        return action;
    }

    private class ZipCopyAction extends CopyActionImpl implements ArchiveCopyAction {
        public ZipCopyAction(FileResolver fileResolver) {
            super(fileResolver, new ZipCopySpecVisitor());
        }

        public File getArchivePath() {
            return Zip.this.getArchivePath();
        }
    }
}
