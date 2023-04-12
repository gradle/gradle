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

package org.gradle.api.publish.ivy.internal.artifact;

import com.google.common.base.Strings;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.publish.ivy.internal.publisher.IvyArtifactInternal;
import org.gradle.api.tasks.TaskDependency;

import javax.annotation.Nullable;

public abstract class AbstractIvyArtifact implements IvyArtifactInternal {
    private final TaskDependency allBuildDependencies;
    private final DefaultTaskDependency additionalBuildDependencies;

    private String name;
    private String type;
    private String extension;
    private String classifier;
    private String conf;

    protected AbstractIvyArtifact(TaskDependencyFactory taskDependencyFactory) {
        this.additionalBuildDependencies = new DefaultTaskDependency();
        this.allBuildDependencies = taskDependencyFactory.visitingDependencies(context -> {
            context.add(getDefaultBuildDependencies());
            additionalBuildDependencies.visitDependencies(context);
        });
    }

    @Override
    public String getName() {
        return name != null ? name : getDefaultName();
    }

    protected abstract String getDefaultName();

    @Override
    public void setName(String name) {
        this.name = Strings.nullToEmpty(name);
    }

    @Override
    public String getType() {
        return type != null ? type : getDefaultType();
    }

    protected abstract String getDefaultType();

    @Override
    public void setType(String type) {
        this.type = Strings.nullToEmpty(type);
    }

    @Override
    public String getExtension() {
        return extension != null ? extension : getDefaultExtension();
    }

    protected abstract String getDefaultExtension();

    @Override
    public void setExtension(String extension) {
        this.extension = Strings.nullToEmpty(extension);
    }

    @Nullable
    @Override
    public String getClassifier() {
        return Strings.emptyToNull(classifier != null ? classifier : getDefaultClassifier());
    }

    protected abstract String getDefaultClassifier();

    @Override
    public void setClassifier(@Nullable String classifier) {
        this.classifier = Strings.nullToEmpty(classifier);
    }

    @Nullable
    @Override
    public String getConf() {
        return Strings.emptyToNull(conf != null ? conf : getDefaultConf());
    }

    protected abstract String getDefaultConf();

    @Override
    public void setConf(@Nullable String conf) {
        this.conf = Strings.nullToEmpty(conf);
    }

    @Override
    public void builtBy(Object... tasks) {
        additionalBuildDependencies.add(tasks);
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return allBuildDependencies;
    }

    protected abstract TaskDependency getDefaultBuildDependencies();

    @Override
    public String toString() {
        return String.format("%s %s:%s:%s:%s", getClass().getSimpleName(), getName(), getType(), getExtension(), getClassifier());
    }
}
