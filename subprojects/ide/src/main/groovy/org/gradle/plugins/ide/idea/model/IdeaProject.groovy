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

package org.gradle.plugins.ide.idea.model

import org.gradle.api.internal.XmlTransformer
import org.gradle.plugins.ide.idea.IdeaPlugin

/**
 * TODO SF: javadoc with a sample
 *
 * Author: Szczepan Faber, created at: 4/4/11
 */
class IdeaProject {

    /**
     * The subprojects that should be mapped to modules in the ipr file. The subprojects will only be mapped if the Idea plugin has been
     * applied to them.
     */
    Set<org.gradle.api.Project> subprojects

    /**
     * The java version used for defining the project sdk.
     */
    String javaVersion

    /**
     * The wildcard resource patterns.
     */
    Set wildcards

    /**
     * Output *.ipr
     */
    File outputFile

    /**
     * Adds a closure to be called when the XML document has been created. The XML is passed to the closure as a
     * parameter in form of a {@link org.gradle.api.artifacts.maven.XmlProvider}. The closure can modify the XML before
     * it is written to the output file.
     *
     * @param closure The closure to execute when the XML has been created.
     */
    public void withXml(Closure closure) {
        xmlTransformer.addAction(closure);
    }

    XmlTransformer xmlTransformer
    Project xmlProject
    PathFactory pathFactory

    void applyXmlProject(Project xmlProject) {
        this.xmlProject = xmlProject
        def modulePaths = getSubprojects().inject(new LinkedHashSet()) { result, subproject ->
            if (subproject.plugins.hasPlugin(IdeaPlugin)) {
                File imlFile = subproject.ideaModule.outputFile
                result << new ModulePath(getPathFactory().relativePath('PROJECT_DIR', imlFile))
            }
            result
        }
        xmlProject.configure(modulePaths, getJavaVersion(), getWildcards())
    }
}