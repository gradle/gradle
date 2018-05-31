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

import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Date;

public class ArchivePublishArtifact extends AbstractPublishArtifact implements ConfigurablePublishArtifact {
    private String name;
    private String extension;
    private String type;
    private String classifier;
    private Date date;
    private File file;

    private Provider<? extends AbstractArchiveTask> archiveTask;

    public ArchivePublishArtifact(Provider<? extends AbstractArchiveTask> archiveTask) {
        super(archiveTask);
        this.archiveTask = archiveTask;
    }

    @Override
    public ArchivePublishArtifact builtBy(Object... tasks) {
        super.builtBy(tasks);
        return this;
    }

    public String getName() {
        if (name != null) {
            return name;
        }

        if (archiveTask.get().getBaseName() != null) {
            return withAppendix(archiveTask.get().getBaseName());
        }
        return archiveTask.get().getAppendix();
    }

    private String withAppendix(String baseName) {
        return baseName + (GUtil.isTrue(archiveTask.get().getAppendix())? "-" + archiveTask.get().getAppendix() : "");
    }

    public String getExtension() {
        return GUtil.elvis(extension, archiveTask.get().getExtension());
    }

    public String getType() {
        return GUtil.elvis(type, archiveTask.get().getExtension());
    }

    public String getClassifier() {
        return GUtil.elvis(classifier, archiveTask.get().getClassifier());
    }

    public File getFile() {
        return GUtil.elvis(file, archiveTask.get().getArchivePath());
    }

    public Date getDate() {
        return GUtil.elvis(date, new Date(archiveTask.get().getArchivePath().lastModified()));
    }

    public AbstractArchiveTask getArchiveTask() {
        return archiveTask.get();
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public void setFile(File file) {
        this.file = file;
    }
}
