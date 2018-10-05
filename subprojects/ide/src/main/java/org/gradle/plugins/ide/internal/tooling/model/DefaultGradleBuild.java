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

package org.gradle.plugins.ide.internal.tooling.model;

import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier;
import org.gradle.tooling.internal.gradle.GradleBuildIdentity;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultGradleBuild implements Serializable, GradleBuildIdentity {
    private PartialBasicGradleProject rootProject;
    private DefaultBuildIdentifier buildIdentifier;
    private Set<PartialBasicGradleProject> projects = new LinkedHashSet<PartialBasicGradleProject>();
    private Set<DefaultGradleBuild> includedBuilds = new LinkedHashSet<DefaultGradleBuild>();
    private Set<DefaultGradleBuild> allBuilds = new LinkedHashSet<DefaultGradleBuild>();

    public PartialBasicGradleProject getRootProject() {
        return rootProject;
    }

    public DefaultGradleBuild setRootProject(PartialBasicGradleProject rootProject) {
        this.rootProject = rootProject;
        this.buildIdentifier = new DefaultBuildIdentifier(rootProject.getRootDir());
        return this;
    }

    public Set<? extends PartialBasicGradleProject> getProjects() {
        return projects;
    }

    public void addProject(PartialBasicGradleProject project) {
        projects.add(project);
    }

    public Set<DefaultGradleBuild> getEditableBuilds() {
        return allBuilds;
    }

    public void addBuilds(Collection<DefaultGradleBuild> builds) {
        allBuilds.addAll(builds);
    }

    public Set<DefaultGradleBuild> getIncludedBuilds() {
        return includedBuilds;
    }

    public void addIncludedBuild(DefaultGradleBuild includedBuild) {
        includedBuilds.add(includedBuild);
    }

    public DefaultBuildIdentifier getBuildIdentifier() {
        return buildIdentifier;
    }

    @Override
    public File getRootDir() {
        return getBuildIdentifier().getRootDir();
    }
}
