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
package org.gradle.plugins.eclipse;


import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.maven.XmlProvider
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.XmlTransformer
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.listener.ActionBroadcast
import org.gradle.plugins.eclipse.model.BuildCommand
import org.gradle.plugins.eclipse.model.Link
import org.gradle.plugins.eclipse.model.Project
import org.gradle.plugins.eclipse.model.internal.ModelFactory

/**
 * Generates an Eclipse <i>.project</i> file.
 *
 * @author Hans Dockter
 */
public class EclipseProject extends ConventionTask {
    private static final LINK_ARGUMENTS = ['name', 'type', 'location', 'locationUri']

    /**
     * The file that is merged into the to be produced project file. This file must not exist.
     */
    File inputFile

    @OutputFile
    /**
     * The output file where to generate the project metadata to.
     */
    File outputFile

    /**
     * The name used for the name of the eclipse project
     */
    String projectName;

    /**
     * A comment used for the eclipse project
     */
    String comment;

    /**
     * The referenced projects of this Eclipse project.
     */
    Set<String> referencedProjects = new LinkedHashSet<String>();

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
    Set<Link> links = new LinkedHashSet<Link>();

    protected ModelFactory modelFactory = new ModelFactory()

    def XmlTransformer withXmlActions = new XmlTransformer();
    def ActionBroadcast<Project> beforeConfiguredActions = new ActionBroadcast<Project>();
    def ActionBroadcast<Project> whenConfiguredActions = new ActionBroadcast<Project>();

    def EclipseProject() {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    void generateXml() {
        Project project = modelFactory.createProject(this)
        project.toXml(getOutputFile())
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
        this.buildCommands.add(new BuildCommand(buildCommand, args))
    }

    /**
     * Adds a build command to the eclipse project.
     *
     * @param buildCommand The name of the build command
     * @see #buildCommand(Map, String)
     */
    void buildCommand(String buildCommand) {
        assert buildCommand != null
        this.buildCommands.add(new BuildCommand(buildCommand))
    }

    /**
     * Adds a link to the eclipse project.
     *
     * @param args A maps with the args for the link. Legal keys for the map are name, type, location and locationUri.
     */
    void link(Map args) {
        def illegalArgs = LINK_ARGUMENTS - args.keySet()
        if (illegalArgs) {
            throw new InvalidUserDataException("You provided illegal argument for a link: " + illegalArgs)
        }
        this.links.add(new Link(args.name, args.type, args.location, args.locationUri))
    }

    /**
     * Adds a closure to be called when the .project XML has been created. The XML is passed to the closure as a
     * parameter in form of a {@link org.gradle.api.artifacts.maven.XmlProvider}. The closure can modify the XML.
     *
     * @param closure The closure to execute when the .project XML has been created.
     */
    void withXml(Closure closure) {
        withXmlActions.addAction(closure);
    }

    /**
     * Adds an action to be called when the .project XML has been created. The XML is passed to the action as a
     * parameter in form of a {@link org.gradle.api.artifacts.maven.XmlProvider}. The action can modify the XML.
     *
     * @param action The action to execute when the .project XML has been created.
     */
    void withXml(Action<? super XmlProvider> action) {
        withXmlActions.addAction(action);
    }

    void beforeConfigured(Closure closure) {
        beforeConfiguredActions.add(closure);
    }

    void whenConfigured(Closure closure) {
        whenConfiguredActions.add(closure);
    }
}
