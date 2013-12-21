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
package org.gradle.ide.visualstudio.tasks
import org.gradle.api.Incubating
import org.gradle.ide.visualstudio.VisualStudioProject
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioProject
import org.gradle.ide.visualstudio.tasks.internal.AbsoluteFileNameTransformer
import org.gradle.ide.visualstudio.tasks.internal.VisualStudioProjectFile
import org.gradle.plugins.ide.api.XmlGeneratorTask

@Incubating
class GenerateProjectFileTask extends XmlGeneratorTask<VisualStudioProjectFile> {
    private DefaultVisualStudioProject visualStudioProject

    void setVisualStudioProject(VisualStudioProject vsProject) {
        this.visualStudioProject = vsProject as DefaultVisualStudioProject
    }

    VisualStudioProject getVisualStudioProject() {
        return visualStudioProject
    }

    @Override
    File getInputFile() {
        return null
    }

    @Override
    File getOutputFile() {
        return visualStudioProject.projectFile.location
    }

    @Override
    protected VisualStudioProjectFile create() {
        return new VisualStudioProjectFile(xmlTransformer, new AbsoluteFileNameTransformer())
    }

    @Override
    protected void configure(VisualStudioProjectFile projectFile) {
        DefaultVisualStudioProject vsProject = visualStudioProject

        projectFile.setProjectUuid(vsProject.uuid)
        vsProject.sourceFiles.each {
            projectFile.addSourceFile(it)
        }
        vsProject.resourceFiles.each {
            projectFile.addResource(it)
        }
        vsProject.headerFiles.each {
            projectFile.addHeaderFile(it)
        }

        vsProject.configurations.each {
            projectFile.addConfiguration(it)
        }

        vsProject.projectReferences.each { DefaultVisualStudioProject projectReference ->
            projectFile.addProjectReference(projectReference)
        }

        vsProject.projectFile.xmlActions.each {
            xmlTransformer.addAction(it)
        }
    }
}