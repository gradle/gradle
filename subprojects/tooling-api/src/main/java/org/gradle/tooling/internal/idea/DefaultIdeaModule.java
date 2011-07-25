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

package org.gradle.tooling.internal.idea;

import org.gradle.api.GradleException;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.HierarchicalElement;
import org.gradle.tooling.model.Task;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptySet;

/**
 * @author: Szczepan Faber, created at: 7/25/11
 */
public class DefaultIdeaModule implements Serializable, IdeaModule {

//    public static final long serialVersionUID = 1L;

    private String name;
    private List<File> contentRoots = new LinkedList<File>();
    private IdeaProject parent;
    boolean inheritOutputDirs;
    File outputDir;
    File testOutputDir;
    File moduleFileDir;
    List<File> sourceDirectories;
    List<File> testDirectories;

    public String getName() {
        return name;
    }

    public DefaultIdeaModule setName(String name) {
        this.name = name;
        return this;
    }

    public List<File> getContentRoots() {
        return contentRoots;
    }

    public DefaultIdeaModule setContentRoots(List<File> contentRoots) {
        this.contentRoots = contentRoots;
        return this;
    }

    public IdeaProject getParent() {
        return parent;
    }

    public DefaultIdeaModule setParent(IdeaProject parent) {
        this.parent = parent;
        return this;
    }

    public File getModuleFileDir() {
        return moduleFileDir;
    }

    public DefaultIdeaModule setModuleFileDir(File moduleFileDir) {
        this.moduleFileDir = moduleFileDir;
        return this;
    }

    public Boolean getInheritOutputDirs() {
        return inheritOutputDirs;
    }

    public DefaultIdeaModule setInheritOutputDirs(boolean inheritOutputDirs) {
        this.inheritOutputDirs = inheritOutputDirs;
        return this;
    }

    public File getOutputDir() {
        return outputDir;
    }

    public DefaultIdeaModule setOutputDir(File outputDir) {
        this.outputDir = outputDir;
        return this;
    }

    public File getTestOutputDir() {
        return testOutputDir;
    }

    public DefaultIdeaModule setTestOutputDir(File testOutputDir) {
        this.testOutputDir = testOutputDir;
        return this;
    }

    public List<File> getSourceDirectories() {
        return sourceDirectories;
    }

    public DefaultIdeaModule setSourceDirectories(List<File> sourceDirectories) {
        this.sourceDirectories = sourceDirectories;
        return this;
    }

    public List<File> getTestDirectories() {
        return testDirectories;
    }

    public DefaultIdeaModule setTestDirectories(List<File> testDirectories) {
        this.testDirectories = testDirectories;
        return this;
    }

    public DomainObjectSet<? extends Task> getTasks() {
        throw new RuntimeException("not yet implemented");
    }

    public DomainObjectSet<? extends HierarchicalElement> getChildren() {
        return new ImmutableDomainObjectSet<HierarchicalElement>((Set) emptySet());
    }

    public String getId() {
        //TODO SF should module have an id? It is a bit awkard to get it.
        throw new GradleException("IdeaModule does not have an id");
    }

    public String getDescription() {
        return null;
    }
}
