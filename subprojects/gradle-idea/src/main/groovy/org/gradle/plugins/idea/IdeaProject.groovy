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
package org.gradle.plugins.idea

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.maven.XmlProvider
import org.gradle.api.internal.XmlTransformer
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.listener.ActionBroadcast
import org.gradle.plugins.idea.model.ModulePath
import org.gradle.plugins.idea.model.PathFactory
import org.gradle.plugins.idea.model.Project

/**
 * Generates an IDEA project file.
 *
 * @author Hans Dockter
 */
public class IdeaProject extends DefaultTask {
    /**
     * The subprojects that should be mapped to modules in the ipr file. The subprojects will only be mapped, if the Idea plugin has been
     * applied to them.
     */
    Set subprojects

    /**
     * The ipr file
     */
    @OutputFile
    File outputFile

    /**
     * The java version used for defining the project sdk.
     */
    @Input
    String javaVersion

    /**
     * The wildcard resource patterns. Must not be null.
     */
    @Input
    Set wildcards

    private ActionBroadcast<Project> beforeConfiguredActions = new ActionBroadcast<Project>();
    private ActionBroadcast<Project> whenConfiguredActions = new ActionBroadcast<Project>();
    private XmlTransformer withXmlActions = new XmlTransformer();

    def IdeaProject() {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    void updateXML() {
        PathFactory pathFactory = getPathFactory()
        Reader xmlreader = outputFile.exists() ? new FileReader(outputFile) : null;
        Set modules = subprojects.inject(new LinkedHashSet()) { result, subproject ->
            if (subproject.plugins.hasPlugin(IdeaPlugin)) {
                File imlFile = subproject.ideaModule.outputFile
                result << new ModulePath(pathFactory.relativePath('PROJECT_DIR', imlFile))
            }
            result
        }
        Project ideaProject = new Project(modules, javaVersion, wildcards, xmlreader,
                beforeConfiguredActions, whenConfiguredActions, withXmlActions, pathFactory)
        outputFile.withWriter {Writer writer -> ideaProject.toXml(writer)}
    }

    private PathFactory getPathFactory() {
        PathFactory factory = new PathFactory()
        factory.addPathVariable('PROJECT_DIR', outputFile.parentFile)
        return factory
    }

    /**
     * Adds a closure to be called when the IPR XML has been created. The XML is passed to the closure as a
     * parameter in form of a {@link org.gradle.api.artifacts.maven.XmlProvider}. The closure can modify the XML.
     *
     * @param closure The closure to execute when the IPR XML has been created.
     * @return this
     */
    IdeaProject withXml(Closure closure) {
        withXmlActions.addAction(closure)
        return this;
    }

    /**
     * Adds an action to be called when the IPR XML has been created. The XML is passed to the action as a
     * parameter in form of a {@link org.gradle.api.artifacts.maven.XmlProvider}. The action can modify the XML.
     *
     * @param closure The action to execute when the IPR XML has been created.
     * @return this
     */
    IdeaProject withXml(Action<? super XmlProvider> action) {
        withXmlActions.addAction(action)
        return this;
    }

    /**
     * Adds a closure to be called after the existing ipr xml or the default xml has been parsed. The information
     * of this xml is used to populate the domain objects that model the customizable aspects of the ipr file.
     * The closure is called before the parameter of this task are added to the domain objects. This hook allows you
     * to do a partial clean for example. You can delete all modules from the existing xml while keeping all the other
     * parts. The closure gets an instance of {@link org.gradle.plugins.idea.model.Project} which can be modified.
     *
     * @param closure The closure to execute when the existing or default ipr xml has been parsed.
     * @return this
     */
    IdeaProject beforeConfigured(Closure closure) {
        beforeConfiguredActions.add(closure);
        return this;
    }

    /**
     * Adds a closure after the domain objects that model the customizable aspects of the ipr file are fully populated.
     * Those objects are populated with the content of the existing or default ipr xml and the arguments of this task.
     * The closure gets an instance of {@link Project} which can be modified.
     *
     * @param closure The closure to execute after the {@link org.gradle.plugins.idea.model.Project} object has been fully populated.
     * @return this
     */
    IdeaProject whenConfigured(Closure closure) {
        whenConfiguredActions.add(closure);
        return this;
    }
}
