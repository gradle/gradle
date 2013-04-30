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

package org.gradle.buildsetup.plugins

import groovy.text.SimpleTemplateEngine
import org.gradle.api.Project
import org.gradle.util.GradleVersion

class JavaLibraryProjectSetupDescriptor implements ProjectSetupDescriptor {

    Project project

    public JavaLibraryProjectSetupDescriptor(Project project) {
        this.project = project;
    }

    String getId() {
        return "java-library";
    }

    org.gradle.internal.Factory<Boolean> getOnlyIf() {
        return new org.gradle.internal.Factory<java.lang.Boolean>() {
            public Boolean create() {
                project.fileTree("src/main/java").files.empty || project.fileTree("src/test/java").files.empty
            }
        };
    }

    void setupLayout(Project project) {
        generateClass("src/main/java", "Library.java")
        generateClass("src/test/java", "LibraryTest.java")
    }

    def generateClass(String sourceRoot, String clazzFileName) {
        File sourceRootFolder = project.file(sourceRoot)
        sourceRootFolder.mkdirs()
        File clazzFile = new File(sourceRootFolder, clazzFileName)
        URL productionClazzFileTemplate = JavaLibraryProjectSetupDescriptor.class.getResource("/org/gradle/buildsetup/tasks/templates/${clazzFileName}.template");
        SimpleTemplateEngine templateEngine = new SimpleTemplateEngine()
        def bindings = [genDate: new Date(), genUser: System.getProperty("user.name"), genGradleVersion: GradleVersion.current().toString()]
        clazzFile.text = templateEngine.createTemplate(productionClazzFileTemplate.text).make(bindings).toString();
    }
}