/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model

import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject

/**
 * Represents the customizable elements of an eclipse project file. (via XML hooks everything is customizable).
 */
class Project extends XmlPersistableConfigurationObject {
    public static final String PROJECT_FILE_NAME = ".project";

    /**
     * The name used for the name of the eclipse project
     */
    String name;

    /**
     * A comment used for the eclipse project
     */
    String comment;

    /**
     * The referenced projects of this Eclipse project.
     */
    Set<String> referencedProjects = new LinkedHashSet<String>()

    /**
     * The natures to be added to this Eclipse project.
     */
    List<String> natures = []

    /**
     * The build commands to be added to this Eclipse project.
     */
    List buildCommands = []

    /**
     * The linkedResources to be added to this Eclipse project.
     */
    Set<Link> linkedResources = new LinkedHashSet<Link>()

    def Project(XmlTransformer xmlTransformer) {
        super(xmlTransformer)
    }

    @Override protected String getDefaultResourceName() {
        return 'defaultProject.xml'
    }

    @Override protected void load(Node xml) {
        this.name = xml.name.text()
        this.comment = xml.comment.text()
        readReferencedProjects()
        readNatures()
        readBuildCommands()
        readLinkedResources()
    }

    private def readReferencedProjects() {
        return xml.projects.project.each {
            this.referencedProjects.add(it.text())
        }
    }

    private def readNatures() {
        return xml.natures.nature.each { this.natures.add(it.text()) }
    }

    private def readBuildCommands() {
        return xml.buildSpec.buildCommand.each { command ->
            Map args = [:]
            command.arguments.dictionary.each { Node it ->
                args[it.key.text()] = it.value.text()
            }
            this.buildCommands.add(new BuildCommand(command.name.text(), args))
        }
    }

    private def readLinkedResources() {
        return xml.linkedResources.link.each { link ->
            this.linkedResources.add(new Link(link.name?.text(), link.type?.text(), link.location?.text(), link.locationURI?.text()))
        }
    }

    def configure(EclipseProject eclipseProject) {
        if (eclipseProject.name) {
            this.name = eclipseProject.name
        }
        if (eclipseProject.comment) {
            this.comment = eclipseProject.comment
        }
        this.referencedProjects.addAll(eclipseProject.referencedProjects)
        this.natures.addAll(eclipseProject.natures)
        this.natures.unique()
        this.buildCommands.addAll(eclipseProject.buildCommands)
        this.buildCommands.unique()
        this.linkedResources.addAll(eclipseProject.linkedResources);
    }

    @Override protected void store(Node xml) {
        ['name', 'comment', 'projects', 'natures', 'buildSpec', 'linkedResources'].each { childNodeName ->
            Node childNode = xml.children().find { it.name() == childNodeName }
            if (childNode) {
                xml.remove(childNode)
            }
        }
        xml.appendNode('name', this.name)
        xml.appendNode('comment', this.comment ?: null)
        addReferencedProjectsToXml()
        addNaturesToXml()
        addBuildSpecToXml()
        addLinkedResourcesToXml()
    }

    private def addReferencedProjectsToXml() {
        def referencedProjectsNode = xml.appendNode('projects')
        this.referencedProjects.each { projectName ->
            referencedProjectsNode.appendNode('project', projectName)
        }
    }

    private def addNaturesToXml() {
        def naturesNode = xml.appendNode('natures')
        this.natures.each { nature ->
            naturesNode.appendNode('nature', nature)
        }
    }

    private def addBuildSpecToXml() {
        def buildSpec = xml.appendNode('buildSpec')
        this.buildCommands.each { command ->
            def commandNode = buildSpec.appendNode('buildCommand')
            commandNode.appendNode('name', command.name)
            def argumentsNode = commandNode.appendNode('arguments')
            command.arguments.each { key, value ->
                def dictionaryNode = argumentsNode.appendNode('dictionary')
                dictionaryNode.appendNode('key', key)
                dictionaryNode.appendNode('value', value)
            }
        }
    }

    private def addLinkedResourcesToXml() {
        def parent = xml.appendNode('linkedResources')
        this.linkedResources.each { link ->
            def linkNode = parent.appendNode('link')
            linkNode.appendNode('name', link.name)
            linkNode.appendNode('type', link.type)
            if (link.location) {
                linkNode.appendNode('location', link.location)
            }
            if (link.locationUri) {
                linkNode.appendNode('locationURI', link.locationUri)
            }
        }
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        Project project = (Project) o;

        if (buildCommands != project.buildCommands) { return false }
        if (comment != project.comment) { return false }
        if (linkedResources != project.linkedResources) { return false }
        if (name != project.name) { return false }
        if (natures != project.natures) { return false }
        if (referencedProjects != project.referencedProjects) { return false }

        return true
    }

    int hashCode() {
        int result;

        result = (name != null ? name.hashCode() : 0);
        result = 31 * result + (comment != null ? comment.hashCode() : 0);
        result = 31 * result + (referencedProjects != null ? referencedProjects.hashCode() : 0);
        result = 31 * result + (natures != null ? natures.hashCode() : 0);
        result = 31 * result + (buildCommands != null ? buildCommands.hashCode() : 0);
        result = 31 * result + (linkedResources != null ? linkedResources.hashCode() : 0);
        return result;
    }


    public String toString() {
        return "Project{" +
                "name='" + name + '\'' +
                ", comment='" + comment + '\'' +
                ", referencedProjects=" + referencedProjects +
                ", natures=" + natures +
                ", buildCommands=" + buildCommands +
                ", linkedResources=" + linkedResources +
                '}';
    }
}
