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

import com.google.common.base.Strings;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;

public abstract class AbstractMavenArtifact implements MavenArtifact {
    private final TaskDependency allBuildDependencies;
    private final DefaultTaskDependency additionalBuildDependencies;
    private String extension;
    private String classifier;

    protected AbstractMavenArtifact() {
        this.additionalBuildDependencies = new DefaultTaskDependency();
        this.allBuildDependencies = new CompositeTaskDependency();
    }

    @Override
    public abstract File getFile();

    @Override
    public final String getExtension() {
        return extension != null ? extension : getDefaultExtension();
    }

    protected abstract String getDefaultExtension();

    @Override
    public final void setExtension(String extension) {
        this.extension = Strings.nullToEmpty(extension);
    }

    @Override
    public final String getClassifier() {
        return Strings.emptyToNull(classifier != null ? classifier : getDefaultClassifier());
    }

    protected abstract String getDefaultClassifier();

    @Override
    public final void setClassifier(String classifier) {
        this.classifier = Strings.nullToEmpty(classifier);
    }

    @Override
    public final void builtBy(Object... tasks) {
        additionalBuildDependencies.add(tasks);
    }

    @Override
    public final TaskDependency getBuildDependencies() {
        return allBuildDependencies;
    }

    protected abstract TaskDependencyInternal getDefaultBuildDependencies();

    @Override
    public final String toString() {
        return getClass().getSimpleName() + " " + getExtension() + ":" + getClassifier();
    }

    private class CompositeTaskDependency extends AbstractTaskDependency {

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            getDefaultBuildDependencies().visitDependencies(context);
            additionalBuildDependencies.visitDependencies(context);
        }
    }
}
