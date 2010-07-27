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
package org.gradle.api.tasks.ide.eclipse;


import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.plugins.eclipse.AbstractXmlGeneratorTask
import org.gradle.plugins.eclipse.model.BuildCommand
import org.gradle.plugins.eclipse.model.Link
import org.gradle.plugins.eclipse.model.Project
import org.gradle.plugins.eclipse.model.internal.ModelFactory

/**
 * Generates an eclipse <i>.project</i> file.
 *
 * @author Hans Dockter
 */
public class EclipseProject extends AbstractXmlGeneratorTask {
    private static final LINK_ARGUMENTS = ['name', 'type', 'location', 'locationUri']

    File inputFile

    @OutputFile
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

    @TaskAction
    void generateXml() {
        Project project = modelFactory.createProject(this)
        project.toXml(getOutputFile())
    }

    void natures(String... natures) {
        assert natures != null
        this.natures.addAll(natures as List)
    }

    void referencedProjects(String... referencedProjects) {
        assert referencedProjects != null
        this.referencedProjects.addAll(referencedProjects as List)
    }

    void buildCommand(Map args, String buildCommand) {
        assert buildCommand != null
        this.buildCommands.add(new BuildCommand(buildCommand, args))
    }

    void buildCommand(String buildCommand) {
        assert buildCommand != null
        this.buildCommands.add(new BuildCommand(buildCommand))
    }

    void link(Map args) {
        def illegalArgs = LINK_ARGUMENTS - args.keySet()
        if (illegalArgs) {
            throw new InvalidUserDataException("You provided illegal argument for a link: " + illegalArgs)
        }
        this.links.add(new Link(args.name, args.type, args.location, args.locationUri))
    }
}
