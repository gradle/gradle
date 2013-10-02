/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.buildinit.plugins.internal

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.FileResolver

abstract class TemplateBasedProjectInitDescriptor extends CompositeProjectInitDescriptor {

    protected final DocumentationRegistry documentationRegistry
    protected final FileResolver fileResolver

    protected TemplateBasedFileGenerator fileGenerator = new TemplateBasedFileGenerator()

    TemplateBasedProjectInitDescriptor(FileResolver fileResolver, DocumentationRegistry documentationRegistry, ProjectInitDescriptor... delegates) {
        super(delegates)
        this.fileResolver = fileResolver
        this.documentationRegistry = documentationRegistry
    }

    TemplateBasedProjectInitDescriptor(FileResolver fileResolver, DocumentationRegistry documentationRegistry) {
        this(fileResolver, documentationRegistry, new ProjectInitDescriptor[0])
    }

    URL getBuildFileTemplate() {
        null
    }

    URL getSettingsTemplate() {
        null
    }

    void generateProject() {
        super.generateProject()
        generateGradleFiles()
        generateProjectSources()
    }

    void generateProjectSources() {
    }

    def generateGradleFiles() {
        LinkedHashMap<String, String> specificBindings = getAdditionalBuildFileTemplateBindings()
        URL buildFileTemplate = getBuildFileTemplate()
        if (buildFileTemplate != null) {
            generateFileFromTemplate(buildFileTemplate, fileResolver.resolve("build.gradle"), specificBindings)
        }
        URL settingsTemplate = getSettingsTemplate()
        if (settingsTemplate != null) {
            generateFileFromTemplate(settingsTemplate, fileResolver.resolve("settings.gradle"), getAdditionalSettingsFileTemplateBindings())
        }
    }

    protected Map getAdditionalBuildFileTemplateBindings() {
        return [ref_userguide_java_tutorial: documentationRegistry.getDocumentationFor("tutorial_java_projects")]
    }

    protected Map getAdditionalSettingsFileTemplateBindings() {
        return [ref_userguide_multiproject: documentationRegistry.getDocumentationFor("multi_project_builds"), rootProjectName: fileResolver.resolve(".").name]
    }

    protected generateFileFromTemplate(URL templateURL, File targetFile, Map specificBindings) {
        fileGenerator.generate(templateURL, targetFile, specificBindings)
    }
}
