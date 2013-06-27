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

package org.gradle.buildsetup.plugins.internal

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.FileResolver

class JavaLibraryProjectSetupDescriptor extends TemplateBasedProjectSetupDescriptor {

    private final FileResolver fileResolver

    public JavaLibraryProjectSetupDescriptor(FileResolver fileResolver, DocumentationRegistry documentationRegistry) {
        super(fileResolver, documentationRegistry);
        this.fileResolver = fileResolver;
    }

    String getId() {
        BuildSetupTypeIds.JAVA_LIBRARY
    }

    URL getBuildFileTemplate() {
        return JavaLibraryProjectSetupDescriptor.class.getResource("/org/gradle/buildsetup/tasks/templates/java-library-build.gradle.template");
    }

    URL getSettingsTemplate() {
        return JavaLibraryProjectSetupDescriptor.class.getResource("/org/gradle/buildsetup/tasks/templates/settings.gradle.template")
    }

    void generateProjectSources() {
        if (fileResolver.resolveFilesAsTree("src/main/java").empty || fileResolver.resolveFilesAsTree("src/test/java").empty) {
            generateClass("src/main/java", "Library.java")
            generateClass("src/test/java", "LibraryTest.java")
        }
    }

    def generateClass(String sourceRoot, String clazzFileName) {
        File sourceRootFolder = fileResolver.resolve(sourceRoot)
        sourceRootFolder.mkdirs()
        File clazzFile = new File(sourceRootFolder, clazzFileName)
        URL productionClazzFileTemplate = JavaLibraryProjectSetupDescriptor.class.getResource("/org/gradle/buildsetup/tasks/templates/${clazzFileName}.template");
        generateFileFromTemplate(productionClazzFileTemplate, clazzFile, [:])
    }
}