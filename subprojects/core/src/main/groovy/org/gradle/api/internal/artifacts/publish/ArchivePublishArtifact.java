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
    private String name;
    private String extension;
    private String type;
    private String classifier;
    private Date date;
    private File file;
    
    private AbstractArchiveTask archiveTask;

    public ArchivePublishArtifact(AbstractArchiveTask archiveTask) {
        super(archiveTask);
        this.archiveTask = archiveTask;
    }

    public String getName() {
        return GUtil.elvis(name, archiveTask.getBaseName() + (GUtil.isTrue(archiveTask.getAppendix()) ? "-" + archiveTask.getAppendix() : ""));
    }

    public String getExtension() {
        return GUtil.elvis(extension, archiveTask.getExtension());
    }

    public String getType() {
        return GUtil.elvis(type, archiveTask.getExtension());
    }

    public String getClassifier() {
        return GUtil.elvis(classifier, archiveTask.getClassifier());
    }

    public File getFile() {
        return GUtil.elvis(file, archiveTask.getArchivePath());
    }

    public Date getDate() {
        return GUtil.elvis(date, new Date(archiveTask.getArchivePath().lastModified()));
    }

    public AbstractArchiveTask getArchiveTask() {
        return archiveTask;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ArchivePublishArtifact that = (ArchivePublishArtifact) o;

        if (!getArchiveTask().equals(that.getArchiveTask())) {
            return false;
        }
        if (getClassifier() != null ? !getClassifier().equals(that.getClassifier()) : that.getClassifier() != null) {
            return false;
        }
        if (getDate() != null ? !getDate().equals(that.getDate()) : that.getDate() != null) {
            return false;
        }
        if (getExtension() != null ? !getExtension().equals(that.getExtension()) : that.getExtension() != null) {
            return false;
        }
        if (getFile() != null ? !getFile().equals(that.getFile()) : that.getFile() != null) {
            return false;
        }
        if (getName() != null ? !getName().equals(that.getName()) : that.getName() != null) {
            return false;
        }
        if (getType() != null ? !getType().equals(that.getType()) : that.getType() != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = getName() != null ? getName().hashCode() : 0;
        result = 31 * result + (getExtension() != null ? getExtension().hashCode() : 0);
        result = 31 * result + (getType() != null ? getType().hashCode() : 0);
        result = 31 * result + (getClassifier() != null ? getClassifier().hashCode() : 0);
        result = 31 * result + (getDate() != null ? getDate().hashCode() : 0);
        result = 31 * result + (getFile() != null ? getFile().hashCode() : 0);
        result = 31 * result + getArchiveTask().hashCode();
        return result;
    }
}
