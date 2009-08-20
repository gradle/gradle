/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.publish;

import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Date;

/**
 * @author Hans Dockter
 */
public class ArchivePublishArtifact extends AbstractPublishArtifact {
    private AbstractArchiveTask archiveTask;

    public ArchivePublishArtifact(AbstractArchiveTask archiveTask) {
        super(archiveTask);
        this.archiveTask = archiveTask;
    }

    public String getName() {
        return archiveTask.getBaseName() + (GUtil.isTrue(archiveTask.getAppendix()) ? "-" + archiveTask.getAppendix() : "");
    }

    public String getExtension() {
        return archiveTask.getExtension();
    }

    public String getType() {
        return archiveTask.getExtension();
    }

    public String getClassifier() {
        return archiveTask.getClassifier();
    }

    public File getFile() {
        return archiveTask.getArchivePath();
    }

    public Date getDate() {
        return new Date(archiveTask.getArchivePath().lastModified());
    }

    public String toString() {
        return String.format("ArchivePublishArtifact $s:%s:%s:%s", getName(), getType(), getExtension(), getClassifier());
    }

    public AbstractArchiveTask getArchiveTask() {
        return archiveTask;
    }
}
