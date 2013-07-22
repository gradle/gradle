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

import org.gradle.internal.reflect.Instantiator
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.plugins.ide.eclipse.model.EclipseProject
import org.gradle.plugins.ide.eclipse.model.Project

/**
 * Generates an Eclipse <code>.project</code> file. If you want to fine tune the eclipse configuration
 * <p>
 * At this moment nearly all configuration is done via {@link EclipseProject}.
 */
class GenerateEclipseProject extends XmlGeneratorTask<Project> {

    /**
     * model for eclipse project (.project) generation
     */
    EclipseProject projectModel

    GenerateEclipseProject() {
        xmlTransformer.indentation = "\t"
        projectModel = services.get(Instantiator).newInstance(EclipseProject, new XmlFileContentMerger(xmlTransformer))
    }

    @Override protected Project create() {
        new Project(xmlTransformer)
    }

    @Override protected void configure(Project project) {
        projectModel.mergeXmlProject(project);
    }

}
