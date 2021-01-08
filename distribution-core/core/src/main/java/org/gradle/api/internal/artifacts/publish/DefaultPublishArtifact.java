/*
 * Copyright 2007 the original author or authors.
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
import org.gradle.api.internal.tasks.TaskResolver;

import java.io.File;
import java.util.Date;

public class DefaultPublishArtifact extends AbstractPublishArtifact implements ConfigurablePublishArtifact {
    private String name;
    private String extension;
    private String type;
    private String classifier;
    private Date date;
    private File file;

    public DefaultPublishArtifact(TaskResolver resolver,
                                  String name, String extension, String type,
                                  String classifier, Date date, File file, Object... tasks) {
        super(resolver, tasks);
        this.name = name;
        this.extension = extension;
        this.type = type;
        this.date = date;
        this.classifier = classifier;
        this.file = file;
    }

    public DefaultPublishArtifact(String name, String extension, String type,
                                  String classifier, Date date, File file, Object... tasks) {
        super(tasks);
        this.name = name;
        this.extension = extension;
        this.type = type;
        this.date = date;
        this.classifier = classifier;
        this.file = file;
    }

    @Override
    public DefaultPublishArtifact builtBy(Object... tasks) {
        super.builtBy(tasks);
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getExtension() {
        return extension;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getClassifier() {
        return classifier;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public Date getDate() {
        return date;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void setExtension(String extension) {
        this.extension = extension;
    }

    @Override
    public void setType(String type) {
        this.type = type;
    }

    @Override
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
    public boolean shouldBePublished() {
        return true;
    }
}
