/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model;

import groovy.lang.Closure;
import groovy.util.Node;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.XmlProvider;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;

class NonRenamableProject extends Project {
    private final Project delegate;

    NonRenamableProject(Project delegate) {
        super(null);
        this.delegate = delegate;
    }

    public void setName(String name) {
        throw new InvalidUserDataException("Configuring eclipse project name in 'beforeMerged' or 'whenMerged' hook is not allowed.");
    }

    public String getDefaultResourceName() {
        return delegate.getDefaultResourceName();
    }

    public String getName() {
        return delegate.getName();
    }

    public String getComment() {
        return delegate.getComment();
    }

    public void setComment(String comment) {
        delegate.setComment(comment);
    }

    public Set<String> getReferencedProjects() {
        return delegate.getReferencedProjects();
    }

    public void setReferencedProjects(Set<String> referencedProjects) {
        delegate.setReferencedProjects(referencedProjects);
    }

    public List<String> getNatures() {
        return delegate.getNatures();
    }

    public void setNatures(List<String> natures) {
        delegate.setNatures(natures);
    }

    public List<BuildCommand> getBuildCommands() {
        return delegate.getBuildCommands();
    }

    public void setBuildCommands(List<BuildCommand> buildCommands) {
        delegate.setBuildCommands(buildCommands);
    }

    public Set<Link> getLinkedResources() {
        return delegate.getLinkedResources();
    }

    public void setLinkedResources(Set<Link> linkedResources) {
        delegate.setLinkedResources(linkedResources);
    }

    public Object configure(EclipseProject eclipseProject) {
        return delegate.configure(eclipseProject);
    }

    public void load(Node xml) {
        delegate.load(xml);
    }

    public void store(Node xml) {
        delegate.store(xml);
    }

    public void load(InputStream inputStream) throws Exception {
        delegate.load(inputStream);
    }

    public void store(OutputStream outputStream) {
        delegate.store(outputStream);
    }

    public Node getXml() {
        return delegate.getXml();
    }

    public void transformAction(Closure action) {
        delegate.transformAction(action);
    }

    public void transformAction(Action<? super XmlProvider> action) {
        delegate.transformAction(action);
    }

    public void load(File inputFile) {
        delegate.load(inputFile);
    }

    public void loadDefaults() {
        delegate.loadDefaults();
    }

    public void store(File outputFile) {
        delegate.store(outputFile);
    }
}
