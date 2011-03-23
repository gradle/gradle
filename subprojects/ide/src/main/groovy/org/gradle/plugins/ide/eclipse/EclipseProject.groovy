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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.XmlGeneratorTask
import org.gradle.plugins.ide.eclipse.model.BuildCommand
import org.gradle.plugins.ide.eclipse.model.Link
import org.gradle.plugins.ide.eclipse.model.Project

/**
 * Generates an Eclipse <code>.project</code> file.
 * <p>
 * Example how to configure eclipse project generation:
 * <pre>
 * allprojects {
 *   apply plugin: 'java'
 *   apply plugin: 'eclipse'
 * }
 *
 * project(':model') {
 *   eclipseProject {
 *     //...
 *   }
 * }
 * </pre>
 * @author Hans Dockter
 */
class EclipseProject extends XmlGeneratorTask<Project> implements DependsOnConfigurer {
    private static final LINK_ARGUMENTS = ['name', 'type', 'location', 'locationUri']

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
     * <pre>
     * project(':someProject') {
     *    eclipseProject {
     *      projectName = 'some-important-project'
     *    }
     * }
     * </pre>
     */
    String projectName

    /**
     * A comment used for the eclipse project
     */
    String comment

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
    List<BuildCommand> buildCommands = []

    /**
     * The links to be added to this Eclipse project.
     */
    Set<Link> links = new LinkedHashSet<Link>()

    EclipseProject() {
        xmlTransformer.indentation = "\t"
    }

    @Override protected Project create() {
        new Project(xmlTransformer)
    }

    @Override protected void configure(Project project) {
        project.configure(this)
    }

    /**
     * Adds natures entries to the eclipse project.
     * @param natures the nature names
     */
    void natures(String... natures) {
        assert natures != null
        this.natures.addAll(natures as List)
    }

    /**
     * Adds project references to the eclipse project.
     *
     * @param referencedProjects The name of the project references.
     */
    void referencedProjects(String... referencedProjects) {
        assert referencedProjects != null
        this.referencedProjects.addAll(referencedProjects as List)
    }

    /**
     * Adds a build command with arguments to the eclipse project.
     *
     * @param args A map with arguments, where the key is the name of the argument and the value the value.
     * @param buildCommand The name of the build command.
     * @see #buildCommand(String)
     */
    void buildCommand(Map args, String buildCommand) {
        assert buildCommand != null
        buildCommands << new BuildCommand(buildCommand, args)
    }

    /**
     * Adds a build command to the eclipse project.
     *
     * @param buildCommand The name of the build command
     * @see #buildCommand(Map, String)
     */
    void buildCommand(String buildCommand) {
        assert buildCommand != null
        buildCommands << new BuildCommand(buildCommand)
    }

    /**
     * Adds a link to the eclipse project.
     *
     * @param args A maps with the args for the link. Legal keys for the map are name, type, location and locationUri.
     */
    void link(Map<String, String> args) {
        def illegalArgs = LINK_ARGUMENTS - args.keySet()
        if (illegalArgs) {
            throw new InvalidUserDataException("You provided illegal argument for a link: " + illegalArgs)
        }
        links << new Link(args.name, args.type, args.location, args.locationUri)
    }
}
