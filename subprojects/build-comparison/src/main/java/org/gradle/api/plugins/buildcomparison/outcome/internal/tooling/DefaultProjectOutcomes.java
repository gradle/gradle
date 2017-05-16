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

package org.gradle.api.plugins.buildcomparison.outcome.internal.tooling;

import com.google.common.collect.Lists;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;
import org.gradle.tooling.model.internal.outcomes.GradleBuildOutcome;
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes;

import java.io.File;
import java.io.Serializable;
import java.util.List;

public class DefaultProjectOutcomes implements ProjectOutcomes, Serializable {
    private final String name;
    private final String projectPath;
    private final String description;
    private final File projectDirectory;
    private final DomainObjectSet<? extends GradleBuildOutcome> outcomes;
    private final ProjectOutcomes parent;
    private final List<ProjectOutcomes> children = Lists.newArrayList();

    public DefaultProjectOutcomes(String name, String projectPath, String description, File projectDirectory,
                                  DomainObjectSet<? extends GradleBuildOutcome> outcomes, ProjectOutcomes parent) {
        this.name = name;
        this.projectPath = projectPath;
        this.description = description;
        this.projectDirectory = projectDirectory;
        this.outcomes = outcomes;
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return projectPath;
    }

    public String getDescription() {
        return description;
    }

    public File getProjectDirectory() {
        return projectDirectory;
    }

    public DomainObjectSet<? extends GradleBuildOutcome> getOutcomes() {
        return outcomes;
    }

    public ProjectOutcomes getParent() {
        return parent;
    }

    public DomainObjectSet<ProjectOutcomes> getChildren() {
        return new ImmutableDomainObjectSet<ProjectOutcomes>(children);
    }

    public void addChild(ProjectOutcomes child) {
        children.add(child);
    }
}
