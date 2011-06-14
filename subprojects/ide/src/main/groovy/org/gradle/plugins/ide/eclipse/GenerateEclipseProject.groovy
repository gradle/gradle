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
package org.gradle.plugins.ide.eclipse

import org.gradle.api.internal.ClassGenerator
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.plugins.ide.eclipse.model.BuildCommand
import org.gradle.plugins.ide.eclipse.model.EclipseProject
import org.gradle.plugins.ide.eclipse.model.Link
import org.gradle.plugins.ide.eclipse.model.Project
import org.gradle.util.DeprecationLogger

/**
 * Generates an Eclipse <code>.project</code> file. If you want to fine tune the eclipse configuration
 * <p>
 * At this moment nearly all configuration is done via {@link EclipseProject}.
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
     * Deprecated. Please use #eclipse.project.name. See examples in {@link EclipseProject}.
     * <p>
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
    @Deprecated
    String getProjectName() {
        DeprecationLogger.nagUser("eclipseProject.projectName", "eclipse.project.name")
        projectModel.name
    }

    @Deprecated
    void setProjectName(String projectName) {
        DeprecationLogger.nagUser("eclipseProject.projectName", "eclipse.project.name")
        projectModel.name = projectName
    }

    /**
     * Deprecated. Please use #eclipse.project.comment. See examples in {@link EclipseProject}.
     * <p>
     * A comment used for the eclipse project
     */
    @Deprecated
    String getComment() {
        DeprecationLogger.nagUser("eclipseProject.comment", "eclipse.project.comment")
        projectModel.comment
    }

    @Deprecated
    void setComment(String comment) {
        DeprecationLogger.nagUser("eclipseProject.comment", "eclipse.project.comment")
        projectModel.comment = comment
    }

    /**
     * Deprecated. Please use #eclipse.project.referencedProjects. See examples in {@link EclipseProject}.
     * <p>
     * The referenced projects of this Eclipse project.
     */
    @Deprecated
    Set<String> getReferencedProjects() {
        DeprecationLogger.nagUser("eclipseProject.referencedProjects", "eclipse.project.referencedProjects")
        projectModel.referencedProjects
    }

    @Deprecated
    void setReferencedProjects(Set<String> referencedProjects) {
        DeprecationLogger.nagUser("eclipseProject.referencedProjects", "eclipse.project.referencedProjects")
        projectModel.referencedProjects = referencedProjects
    }

    /**
     * Deprecated. Please use #eclipse.project.natures. See examples in {@link EclipseProject}.
     * <p>
     * The natures to be added to this Eclipse project.
     */
    @Deprecated
    List<String> getNatures() {
        DeprecationLogger.nagUser("eclipseProject.natures", "eclipse.project.natures")
        projectModel.natures
    }

    @Deprecated
    void setNatures(List<String> natures) {
        DeprecationLogger.nagUser("eclipseProject.natures", "eclipse.project.natures")
        projectModel.natures = natures
    }

    /**
     * Deprecated. Please use #eclipse.project.buildCommands. See examples in {@link EclipseProject}.
     * <p>
     * The build commands to be added to this Eclipse project.
     */
    @Deprecated
    List<BuildCommand> getBuildCommands() {
        DeprecationLogger.nagUser("eclipseProject.buildCommands", "eclipse.project.buildCommands")
        projectModel.buildCommands
    }

    @Deprecated
    void setBuildCommands(List<BuildCommand> buildCommands) {
        DeprecationLogger.nagUser("eclipseProject.buildCommands", "eclipse.project.buildCommands")
        projectModel.buildCommands = buildCommands
    }

    /**
     * Deprecated. Please use #eclipse.project.linkedResources. See examples in {@link EclipseProject}.
     * <p>
     * The linked resources to be added to this Eclipse project.
     */
    @Deprecated
    Set<Link> getLinks() {
        DeprecationLogger.nagUser("eclipseProject.links", "eclipse.project.linkedResources")
        projectModel.linkedResources
    }

    @Deprecated
    void setLinks(Set<Link> links) {
        DeprecationLogger.nagUser("eclipseProject.links", "eclipse.project.linkedResources")
        projectModel.linkedResources = links
    }

    /**
     * Deprecated. Please use #eclipse.project.natures. See examples in {@link EclipseProject}.
     * <p>
     * Adds natures entries to the eclipse project.
     * @param natures the nature names
     */
    @Deprecated
    void natures(String... natures) {
        DeprecationLogger.nagUser("eclipseProject.natures", "eclipse.project.natures")
        projectModel.natures(natures)
    }

    /**
     * Deprecated. Please use #eclipse.project.referencedProjects. See examples in {@link EclipseProject}.
     * <p>
     * Adds project references to the eclipse project.
     *
     * @param referencedProjects The name of the project references.
     */
    @Deprecated
    void referencedProjects(String... referencedProjects) {
        DeprecationLogger.nagUser("eclipseProject.referencedProjects", "eclipse.project.referencedProjects")
        projectModel.referencedProjects(referencedProjects)
    }

    /**
     * Deprecated. Please use #eclipse.project.buildCommand. See examples in {@link EclipseProject}.
     * <p>
     * Adds a build command with arguments to the eclipse project.
     *
     * @param args A map with arguments, where the key is the name of the argument and the value the value.
     * @param buildCommand The name of the build command.
     * @see #buildCommand(String)
     */
    @Deprecated
    void buildCommand(Map args, String buildCommand) {
        DeprecationLogger.nagUser("eclipseProject.buildCommand", "eclipse.project.buildCommand")
        projectModel.buildCommand(args, buildCommand)
    }

    /**
     * Deprecated. Please use #eclipse.project.buildCommand. See examples in {@link EclipseProject}.
     * <p>
     * Adds a build command to the eclipse project.
     *
     * @param buildCommand The name of the build command
     * @see #buildCommand(Map, String)
     */
    @Deprecated
    void buildCommand(String buildCommand) {
        DeprecationLogger.nagUser("eclipseProject.buildCommand", "eclipse.project.buildCommand")
        projectModel.buildCommand(buildCommand)
    }

    /**
     * Deprecated. Please use #eclipse.project.linkedResource. See examples in {@link EclipseProject}.
     * <p>
     * Adds a link to the eclipse project.
     *
     * @param args A maps with the args for the link. Legal keys for the map are name, type, location and locationUri.
     */
    @Deprecated
    void link(Map<String, String> args) {
        DeprecationLogger.nagUser("eclipseProject.link", "eclipse.project.linkedResource")
        projectModel.linkedResource(args)
    }
}
