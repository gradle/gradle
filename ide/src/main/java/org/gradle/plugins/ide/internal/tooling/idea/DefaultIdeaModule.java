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

import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.internal.gradle.GradleProjectIdentity;
import org.gradle.tooling.model.idea.IdeaCompilerOutput;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class DefaultIdeaModule implements Serializable, GradleProjectIdentity {
    private String name;
    private List<DefaultIdeaContentRoot> contentRoots = new LinkedList<DefaultIdeaContentRoot>();
    private DefaultIdeaProject parent;

    private List<DefaultIdeaDependency> dependencies = new LinkedList<DefaultIdeaDependency>();
    private DefaultGradleProject gradleProject;

    private IdeaCompilerOutput compilerOutput;

    private DefaultIdeaJavaLanguageSettings javaLanguageSettings;
    private String jdkName;

    public String getName() {
        return name;
    }

    public DefaultIdeaModule setName(String name) {
        this.name = name;
        return this;
    }

    public Collection<DefaultIdeaContentRoot> getContentRoots() {
        return contentRoots;
    }

    public DefaultIdeaModule setContentRoots(List<DefaultIdeaContentRoot> contentRoots) {
        this.contentRoots = contentRoots;
        return this;
    }

    public DefaultIdeaProject getParent() {
        return parent;
    }

    public DefaultIdeaProject getProject() {
        return parent;
    }

    public DefaultIdeaModule setParent(DefaultIdeaProject parent) {
        this.parent = parent;
        return this;
    }

    public Collection<DefaultIdeaDependency> getDependencies() {
        return dependencies;
    }

    public DefaultIdeaModule setDependencies(List<DefaultIdeaDependency> dependencies) {
        this.dependencies = dependencies;
        return this;
    }

    public Collection<Object> getChildren() {
        return Collections.emptySet();
    }

    public String getDescription() {
        return null;
    }

    public DefaultGradleProject getGradleProject() {
        return gradleProject;
    }

    public DefaultIdeaModule setGradleProject(DefaultGradleProject gradleProject) {
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

    public DefaultIdeaJavaLanguageSettings getJavaLanguageSettings() {
        return javaLanguageSettings;
    }

    public DefaultIdeaModule setJavaLanguageSettings(DefaultIdeaJavaLanguageSettings javaLanguageSettings) {
        this.javaLanguageSettings = javaLanguageSettings;
        return this;
    }

    public String getJdkName() {
        return jdkName;
    }

    public DefaultIdeaModule setJdkName(String jdkName) {
        this.jdkName = jdkName;
        return this;
    }

    public DefaultProjectIdentifier getProjectIdentifier() {
        return gradleProject.getProjectIdentifier();
    }

    @Override
    public String getProjectPath() {
        return getProjectIdentifier().getProjectPath();
    }

    @Override
    public File getRootDir() {
        return getProjectIdentifier().getBuildIdentifier().getRootDir();
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
