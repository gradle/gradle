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
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaSourceDirectory;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultIdeaContentRoot implements IdeaContentRoot, Serializable {

    File rootDirectory;
    Set<IdeaSourceDirectory> sourceDirectories = new LinkedHashSet<IdeaSourceDirectory>();
    Set<IdeaSourceDirectory> generatedSourceDirectories = new LinkedHashSet<IdeaSourceDirectory>();
    Set<IdeaSourceDirectory> testDirectories = new LinkedHashSet<IdeaSourceDirectory>();
    Set<IdeaSourceDirectory> generatedTestDirectories = new LinkedHashSet<IdeaSourceDirectory>();
    Set<File> excludeDirectories = new LinkedHashSet<File>();

    public File getRootDirectory() {
        return rootDirectory;
    }

    public DefaultIdeaContentRoot setRootDirectory(File rootDirectory) {
        this.rootDirectory = rootDirectory;
        return this;
    }

    public DomainObjectSet<IdeaSourceDirectory> getSourceDirectories() {
        return new ImmutableDomainObjectSet<IdeaSourceDirectory>(sourceDirectories);
    }

    public DefaultIdeaContentRoot setSourceDirectories(Set<IdeaSourceDirectory> sourceDirectories) {
        this.sourceDirectories = sourceDirectories;
        return this;
    }

    public DomainObjectSet<IdeaSourceDirectory> getGeneratedSourceDirectories() {
        return new ImmutableDomainObjectSet<IdeaSourceDirectory>(generatedSourceDirectories);
    }

    public DefaultIdeaContentRoot setGeneratedSourceDirectories(Set<IdeaSourceDirectory> generatedSourceDirectories) {
        this.generatedSourceDirectories = generatedSourceDirectories;
        return this;
    }

    public DomainObjectSet<IdeaSourceDirectory> getTestDirectories() {
        return new ImmutableDomainObjectSet<IdeaSourceDirectory>(testDirectories);
    }

    public DefaultIdeaContentRoot setTestDirectories(Set<IdeaSourceDirectory> testDirectories) {
        this.testDirectories = testDirectories;
        return this;
    }

    public DomainObjectSet<? extends IdeaSourceDirectory> getGeneratedTestDirectories() {
        return new ImmutableDomainObjectSet<IdeaSourceDirectory>(generatedTestDirectories);
    }

    public DefaultIdeaContentRoot setGeneratedTestDirectories(Set<IdeaSourceDirectory> generatedTestDirectories) {
        this.generatedTestDirectories = generatedTestDirectories;
        return this;
    }

    public Set<File> getExcludeDirectories() {
        return excludeDirectories;
    }

    public DefaultIdeaContentRoot setExcludeDirectories(Set<File> excludeDirectories) {
        this.excludeDirectories = excludeDirectories;
        return this;
    }

    public String toString() {
        return "IdeaContentRoot{"
                + "rootDirectory=" + rootDirectory
                + ", sourceDirectories count=" + sourceDirectories.size()
                + ", generatedSourceDirectories count=" + generatedSourceDirectories.size()
                + ", testDirectories count=" + testDirectories.size()
                + ", generatedSourceDirectories count=" + generatedTestDirectories.size()
                + ", excludeDirectories count=" + excludeDirectories.size()
                + '}';
    }
}