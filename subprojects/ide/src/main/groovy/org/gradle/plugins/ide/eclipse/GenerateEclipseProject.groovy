/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import org.gradle.api.internal.ClassGenerator
import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.plugins.ide.eclipse.model.*
import org.gradle.plugins.ide.internal.XmlFileContentMerger

/**
 * Generates an Eclipse <code>.project</code> file. If you want to fine tune the eclipse configuration
 * <p>
 * Please refer to more interesting examples in {@link EclipseProject}.
 * <p>
 * Example:
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'eclipse'
 *
 * eclipseProject {
 *   doLast {
 *     //...
 *   }
 * }
 * </pre>
 *
 * @author Hans Dockter
 */
class GenerateEclipseProject extends XmlGeneratorTask<Project> {

    /**
     * model for eclipse project (.project) generation
     */
    EclipseProject projectModel

    GenerateEclipseProject() {
        xmlTransformer.indentation = "\t"
        projectModel = services.get(ClassGenerator).newInstance(EclipseProject, [file: new XmlFileContentMerger(xmlTransformer)])
    }

    @Override protected Project create() {
        new Project(xmlTransformer)
    }

    @Override protected void configure(Project project) {
        projectModel.mergeXmlProject(project);
    }

    /**
     * Configures eclipse project name. It is <b>optional</b> because the task should configure it correctly for you.
     * By default it will try to use the <b>project.name</b> or prefix it with a part of a <b>project.path</b>
     * to make sure the moduleName is unique in the scope of a multi-module build.
     * The 'uniqeness' of a module name is required for correct import
     * into Eclipse and the task will make sure the name is unique.
     * <p>
     * The logic that makes sure project names are uniqe is available <b>since</b> 1.0-milestone-2
     * <p>
     * In case you need to override the default projectName this is the way to go:
     * <pre autoTested=''>
     * apply plugin: 'eclipse'
     *
     * eclipseProject {
     *   projectName = 'some-important-project'
     * }
     * </pre>
     */
    String getProjectName() {
        projectModel.name
    }

    void setProjectName(String projectName) {
        projectModel.name = projectName
    }

    /**
     * A comment used for the eclipse project
     */
    String getComment() {
        projectModel.comment
    }

    void setComment(String comment) {
        projectModel.comment = comment
    }

    /**
     * The referenced projects of this Eclipse project.
     */
    Set<String> getReferencedProjects() {
        projectModel.referencedProjects
    }

    void setReferencedProjects(Set<String> referencedProjects) {
        projectModel.referencedProjects = referencedProjects
    }

    /**
     * The natures to be added to this Eclipse project.
     */
    List<String> getNatures() {
        projectModel.natures
    }

    void setNatures(List<String> natures) {
        projectModel.natures = natures
    }

    /**
     * The build commands to be added to this Eclipse project.
     */
    List<BuildCommand> getBuildCommands() {
        projectModel.buildCommands
    }

    void setBuildCommands(List<BuildCommand> buildCommands) {
        projectModel.buildCommands = buildCommands
    }

    /**
     * The linked resources to be added to this Eclipse project.
     */
    Set<Link> getLinks() {
        projectModel.linkedResources
    }

    void setLinks(Set<Link> links) {
        projectModel.linkedResources = links
    }

    /**
     * Adds natures entries to the eclipse project.
     * @param natures the nature names
     */
    void natures(String... natures) {
        projectModel.natures(natures)
    }

    /**
     * Adds project references to the eclipse project.
     *
     * @param referencedProjects The name of the project references.
     */
    void referencedProjects(String... referencedProjects) {
        projectModel.referencedProjects(referencedProjects)
    }

    /**
     * Adds a build command with arguments to the eclipse project.
     *
     * @param args A map with arguments, where the key is the name of the argument and the value the value.
     * @param buildCommand The name of the build command.
     * @see #buildCommand(String)
     */
    void buildCommand(Map args, String buildCommand) {
        projectModel.buildCommand(args, buildCommand)
    }

    /**
     * Adds a build command to the eclipse project.
     *
     * @param buildCommand The name of the build command
     * @see #buildCommand(Map, String)
     */
    void buildCommand(String buildCommand) {
        projectModel.buildCommand(buildCommand)
    }

    /**
     * Adds a link to the eclipse project.
     *
     * @param args A maps with the args for the link. Legal keys for the map are name, type, location and locationUri.
     */
    void link(Map<String, String> args) {
        projectModel.linkedResource(args)
    }
}
