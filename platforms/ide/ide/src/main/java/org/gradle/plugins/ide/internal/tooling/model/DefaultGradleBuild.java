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

import org.gradle.tooling.Failure;
import org.gradle.tooling.events.problems.Problem;
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier;
import org.gradle.tooling.internal.gradle.GradleBuildIdentity;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultGradleBuild implements Serializable, GradleBuildIdentity {
    private boolean failed = false;
    private Failure failure;
    private PartialBasicGradleProject rootProject;
    private DefaultBuildIdentifier buildIdentifier;
    private final Set<PartialBasicGradleProject> projects = new LinkedHashSet<>();
    private final Set<DefaultGradleBuild> includedBuilds = new LinkedHashSet<>();
    private final Set<DefaultGradleBuild> allBuilds = new LinkedHashSet<>();
    private List<Problem> problems = Collections.emptyList();

    @Override
    public String toString() {
        return buildIdentifier.toString();
    }

    public PartialBasicGradleProject getRootProject() {
        return rootProject;
    }

    public DefaultGradleBuild setRootProject(PartialBasicGradleProject rootProject) {
        this.rootProject = rootProject;
        this.setBuildIdentifier(new DefaultBuildIdentifier(rootProject.getRootDir()));
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
        return  buildIdentifier.getRootDir();
    }

    public DefaultGradleBuild setFailure(Failure failure) {
        this.failed = failure != null;
        this.failure = failure;
        return this;
    }

    public boolean didItFail(){
        return failed || allBuilds.stream().anyMatch(DefaultGradleBuild::didItFail) || includedBuilds.stream().anyMatch(DefaultGradleBuild::didItFail);
    }

    public Failure getFailure() {
        return failure;
    }

    public void setBuildIdentifier(DefaultBuildIdentifier buildIdentifier) {
        this.buildIdentifier = buildIdentifier;
    }

    public void setProblems(List<Problem> problems) {
        this.problems = problems;
    }

    public List<Problem> getProblems() {
        return problems;
    }
}
