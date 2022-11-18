/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.PublishArtifactInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.util.internal.GUtil;

import java.io.File;
import java.util.Date;

public class DecoratingPublishArtifact extends AbstractPublishArtifact implements ConfigurablePublishArtifact {
    private String name;
    private String extension;
    private String type;
    private String classifier;
    private final PublishArtifact publishArtifact;
    private boolean classifierSet;

    public DecoratingPublishArtifact(TaskDependencyFactory taskDependencyFactory, PublishArtifact publishArtifact) {
        super(taskDependencyFactory, publishArtifact.getBuildDependencies());
        this.publishArtifact = publishArtifact;
    }

    public PublishArtifact getPublishArtifact() {
        return publishArtifact;
    }

    @Override
    public DecoratingPublishArtifact builtBy(Object... tasks) {
        super.builtBy(tasks);
        return this;
    }

    @Override
    public String getName() {
        return GUtil.getOrDefault(name, publishArtifact::getName);
    }

    @Override
    public String getExtension() {
        return GUtil.getOrDefault(extension, publishArtifact::getExtension);
    }

    @Override
    public String getType() {
        return GUtil.getOrDefault(type, publishArtifact::getType);
    }

    @Override
    public String getClassifier() {
        if (classifierSet) {
            return classifier;
        }
        return publishArtifact.getClassifier();
    }

    @Override
    public File getFile() {
        return publishArtifact.getFile();
    }

    @Override
    public Date getDate() {
        return publishArtifact.getDate();
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
        this.classifierSet = true;
    }

    @Override
    public boolean shouldBePublished() {
        return PublishArtifactInternal.shouldBePublished(publishArtifact);
    }
}
