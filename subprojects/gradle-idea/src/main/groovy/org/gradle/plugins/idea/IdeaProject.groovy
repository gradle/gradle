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

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.XmlGeneratorTask
import org.gradle.plugins.idea.model.ModulePath
import org.gradle.plugins.idea.model.PathFactory
import org.gradle.plugins.idea.model.Project

/**
 * Generates an IDEA project file.
 *
 * @author Hans Dockter
 */
public class IdeaProject extends XmlGeneratorTask<Project> {
    /**
     * The subprojects that should be mapped to modules in the ipr file. The subprojects will only be mapped, if the Idea plugin has been
     * applied to them.
     */
    Set subprojects

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

    Project create() {
        return new Project(xmlTransformer, getPathFactory())
    }

    void configure(Project ideaProject) {
        Set modules = subprojects.inject(new LinkedHashSet()) { result, subproject ->
            if (subproject.plugins.hasPlugin(IdeaPlugin)) {
                File imlFile = subproject.ideaModule.outputFile
                result << new ModulePath(pathFactory.relativePath('PROJECT_DIR', imlFile))
            }
            result
        }
        ideaProject.configure(modules, javaVersion, wildcards)
    }

    PathFactory getPathFactory() {
        PathFactory factory = new PathFactory()
        factory.addPathVariable('PROJECT_DIR', outputFile.parentFile)
        return factory
    }
}
