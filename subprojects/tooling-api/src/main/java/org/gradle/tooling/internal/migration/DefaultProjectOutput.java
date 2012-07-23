/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.tooling.internal.migration;

import com.google.common.collect.Lists;

import org.gradle.tooling.internal.protocol.InternalProjectOutput;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;
import org.gradle.tooling.model.internal.migration.ProjectOutput;
import org.gradle.tooling.model.internal.migration.TaskOutput;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

public class DefaultProjectOutput implements InternalProjectOutput, ProjectOutput, Serializable {
    private final String name;
    private final String path;
    private final String description;
    private final File projectDirectory;
    private final Set<TaskOutput> taskOutputs;
    private final ProjectOutput parent;
    private final List<ProjectOutput> children = Lists.newArrayList();

    public DefaultProjectOutput(String name, String path, String description, File projectDirectory, Set<TaskOutput> taskOutputs, ProjectOutput parent) {
        this.name = name;
        this.path = path;
        this.description = description;
        this.projectDirectory = projectDirectory;
        this.taskOutputs = taskOutputs;
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getDescription() {
        return description;
    }

    public File getProjectDirectory() {
        return projectDirectory;
    }

    public Set<TaskOutput> getTaskOutputs() {
        return taskOutputs;
    }

    public ProjectOutput getParent() {
        return parent;
    }

    public DomainObjectSet<ProjectOutput> getChildren() {
        return new ImmutableDomainObjectSet<ProjectOutput>(children);
    }

    public void addChild(ProjectOutput child) {
        children.add(child);
    }
}
