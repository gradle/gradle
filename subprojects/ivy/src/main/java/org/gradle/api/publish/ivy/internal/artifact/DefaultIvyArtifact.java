/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.artifact;

import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.util.GUtil;

import java.io.File;

public class DefaultIvyArtifact implements IvyArtifact {
    private final DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
    private final File file;
    private String name;
    private String extension;
    private String type;
    private String classifier;
    private String conf;

    public DefaultIvyArtifact(File file, String name, String extension, String type, String classifier) {
        this.file = file;
        this.name = name;

        this.extension = extension;
        this.type = type;
        // Handle empty classifiers that come from PublishArtifact and AbstractArchiveTask
        this.classifier = GUtil.elvis(classifier, null);
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
    
    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    public String getConf() {
        return conf;
    }

    public void setConf(String conf) {
        this.conf = conf;
    }

    public void builtBy(Object... tasks) {
        buildDependencies.add(tasks);
    }

    public TaskDependency getBuildDependencies() {
        return buildDependencies;
    }

    @Override
    public String toString() {
        return String.format("%s %s:%s:%s:%s", getClass().getSimpleName(), getName(), getType(), getExtension(), getClassifier());
    }
}
