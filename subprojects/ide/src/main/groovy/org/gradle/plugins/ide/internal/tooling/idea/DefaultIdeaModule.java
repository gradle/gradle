/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling.idea;

import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.HierarchicalElement;
import org.gradle.tooling.model.Task;
import org.gradle.tooling.model.idea.*;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class DefaultIdeaModule implements Serializable, IdeaModule {

    private String name;
    private List<? extends IdeaContentRoot> contentRoots = new LinkedList<IdeaContentRoot>();
    private IdeaProject parent;

    private List<IdeaDependency> dependencies = new LinkedList<IdeaDependency>();
    private GradleProject gradleProject;

    private IdeaCompilerOutput compilerOutput;

    public String getName() {
        return name;
    }

    public DefaultIdeaModule setName(String name) {
        this.name = name;
        return this;
    }

    public DomainObjectSet<? extends IdeaContentRoot> getContentRoots() {
        return new ImmutableDomainObjectSet<IdeaContentRoot>(contentRoots);
    }

    public DefaultIdeaModule setContentRoots(List<? extends IdeaContentRoot> contentRoots) {
        this.contentRoots = contentRoots;
        return this;
    }

    public IdeaProject getParent() {
        return parent;
    }

    public IdeaProject getProject() {
        return parent;
    }

    public DefaultIdeaModule setParent(IdeaProject parent) {
        this.parent = parent;
        return this;
    }

    public DomainObjectSet<IdeaDependency> getDependencies() {
        return new ImmutableDomainObjectSet<IdeaDependency>(dependencies);
    }

    public DefaultIdeaModule setDependencies(List<IdeaDependency> dependencies) {
        this.dependencies = dependencies;
        return this;
    }

    public DomainObjectSet<? extends Task> getTasks() {
        throw new RuntimeException("not yet implemented");
    }

    public DomainObjectSet<? extends HierarchicalElement> getChildren() {
        return new ImmutableDomainObjectSet<HierarchicalElement>(Collections.<HierarchicalElement>emptySet());
    }

    public String getDescription() {
        return null;
    }

    public GradleProject getGradleProject() {
        return gradleProject;
    }

    public DefaultIdeaModule setGradleProject(GradleProject gradleProject) {
        this.gradleProject = gradleProject;
        return this;
    }

    public IdeaCompilerOutput getCompilerOutput() {
        return compilerOutput;
    }

    public DefaultIdeaModule setCompilerOutput(IdeaCompilerOutput compilerOutput) {
        this.compilerOutput = compilerOutput;
        return this;
    }

    @Override
    public String toString() {
        return "IdeaModule{"
                + "name='" + name + '\''
                + ", gradleProject='" + gradleProject + '\''
                + ", contentRoots=" + contentRoots
                + ", compilerOutput=" + compilerOutput
                + ", dependencies count=" + dependencies.size()
                + '}';
    }
}
