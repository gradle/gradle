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

package org.gradle.api.publish.maven.internal.artifact;

import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.util.GUtil;

import java.io.File;

public class DefaultMavenArtifact implements MavenArtifact {
    private final DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
    private final File file;
    private String extension;
    private String classifier;

    public DefaultMavenArtifact(File file, String extension, String classifier) {
        this.file = file;
        this.extension = extension;
        // Handle empty classifiers that come from PublishArtifact and AbstractArchiveTask
        this.classifier = GUtil.elvis(classifier, null);
    }

    public File getFile() {
        return file;
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

    public void builtBy(Object... tasks) {
        buildDependencies.add(tasks);
    }

    public TaskDependency getBuildDependencies() {
        return buildDependencies;
    }

    @Override
    public String toString() {
        return String.format("%s %s:%s", getClass().getSimpleName(), getExtension(), getClassifier());
    }
}
