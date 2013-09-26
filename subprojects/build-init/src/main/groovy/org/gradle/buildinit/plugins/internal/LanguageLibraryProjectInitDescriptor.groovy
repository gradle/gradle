/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildinit.plugins.internal;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.FileResolver;

class LanguageLibraryProjectInitDescriptor extends TemplateBasedProjectInitDescriptor {

    protected final String id;
    protected final String templatepackage

    LanguageLibraryProjectInitDescriptor(String id, FileResolver fileResolver, DocumentationRegistry documentationRegistry){
        super(fileResolver, documentationRegistry);
        this.id = id;
        this.templatepackage = id.replaceAll("-", "") // - IS Not allowed as packagename so remove it
    }

    String getId() {
        return id;
    }

    @Override
    URL getBuildFileTemplate() {
        return getClass().getResource("/org/gradle/buildinit/tasks/templates/${templatepackage}/build.gradle.template");
    }

    @Override
    URL getSettingsTemplate() {
        return getClass().getResource("/org/gradle/buildinit/tasks/templates/settings.gradle.template")
    }

    protected generateClass(String sourceRoot, String clazzFileName) {
        File sourceRootFolder = fileResolver.resolve(sourceRoot)
        sourceRootFolder.mkdirs()
        File clazzFile = new File(sourceRootFolder, clazzFileName)
        URL productionClazzFileTemplate = getClass().getResource("/org/gradle/buildinit/tasks/templates/${templatepackage}/${clazzFileName}.template");
        generateFileFromTemplate(productionClazzFileTemplate, clazzFile, [:])
    }

}
