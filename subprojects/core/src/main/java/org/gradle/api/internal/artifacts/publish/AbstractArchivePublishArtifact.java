/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Date;

abstract public class AbstractArchivePublishArtifact extends AbstractPublishArtifact implements ConfigurablePublishArtifact {

    private String name;
    private String extension;
    private String type;
    private String classifier;
    private Date date;
    private File file;

    public AbstractArchivePublishArtifact(Object... tasks) {
        super(tasks);
    }

    public String getName() {
        if (name != null) {
            return name;
        }

        if (getArchiveTask().getBaseName() != null) {
            return withAppendix(getArchiveTask().getBaseName());
        }
        return getArchiveTask().getAppendix();
    }

    private String withAppendix(String baseName) {
        return baseName + (GUtil.isTrue(getArchiveTask().getAppendix())? "-" + getArchiveTask().getAppendix() : "");
    }

    public String getExtension() {
        return GUtil.elvis(extension, getArchiveTask().getExtension());
    }

    public String getType() {
        return GUtil.elvis(type, getArchiveTask().getExtension());
    }

    public String getClassifier() {
        return GUtil.elvis(classifier, getArchiveTask().getClassifier());
    }

    public File getFile() {
        return GUtil.elvis(file, getArchiveTask().getArchivePath());
    }

    public Date getDate() {
        return GUtil.elvis(date, new Date(getArchiveTask().getArchivePath().lastModified()));
    }

    @Override
    public AbstractArchivePublishArtifact builtBy(Object... tasks) {
        super.builtBy(tasks);
        return this;
    }

    abstract public AbstractArchiveTask getArchiveTask();

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
