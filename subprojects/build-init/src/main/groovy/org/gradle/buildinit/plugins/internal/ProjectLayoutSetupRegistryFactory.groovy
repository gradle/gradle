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
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider
import org.gradle.api.internal.file.FileResolver

class ProjectLayoutSetupRegistryFactory {
    private final DocumentationRegistry documentationRegistry
    private final MavenSettingsProvider mavenSettingsProvider
    private final FileResolver fileResolver

    ProjectLayoutSetupRegistry createProjectLayoutSetupRegistry() {
        DefaultTemplateLibraryVersionProvider libraryVersionProvider = new DefaultTemplateLibraryVersionProvider();
        ProjectLayoutSetupRegistry registry = new ProjectLayoutSetupRegistry()

        // TODO maybe referencing the implementation class here is enough and instantiation
        // should be defererred when descriptor is requested.
        TemplateOperationBuilder templateOperationBuilder = new TemplateOperationBuilder("/org/gradle/buildinit/tasks/templates", fileResolver, documentationRegistry)

        TemplateOperation settingsTemplateOperation = templateOperationBuilder.newTemplateOperation()
                .withTemplate("settings.gradle.template")
                .withTarget("settings.gradle")
                .withDocumentationBindings(ref_userguide_multiproject: "multi_project_builds")
                .withBindings(rootProjectName: fileResolver.resolve(".").name)
                .create()

        TemplateOperation javalibBuildFileTemplateOperation = templateOperationBuilder.newTemplateOperation()
                .withTemplate("javalibrary/build.gradle.template")
                .withTarget("build.gradle")
                .withDocumentationBindings(ref_userguide_java_tutorial: "tutorial_java_projects")
                .withBindings(junitVersion: libraryVersionProvider.getVersion("junit"))
                .create()

        TemplateOperation javalibraryTemplateOperation = templateOperationBuilder.newTemplateOperation()
                .withTemplate("javalibrary/Library.java.template")
                .withTarget("src/main/java/Library.java")
                .create()

        TemplateOperation javalibraryTestTemplateOperation = templateOperationBuilder.newTemplateOperation()
                .withTemplate("javalibrary/LibraryTest.java.template")
                .withTarget("src/test/java/LibraryTest.java")
                .create()

        registry.add(BuildInitTypeIds.JAVA_LIBRARY,
                new TemplateBasedProjectInitDescriptor(settingsTemplateOperation,
                        javalibBuildFileTemplateOperation,
                        new ConditionalTemplateOperation({ fileResolver.resolveFilesAsTree("src/main/java").empty || fileResolver.resolveFilesAsTree("src/main/java").empty },
                                javalibraryTemplateOperation, javalibraryTestTemplateOperation)))

        TemplateOperation scalalibBuildFileTemplateOperation = templateOperationBuilder.newTemplateOperation()
                .withTemplate("scalalibrary/build.gradle.template")
                .withTarget("build.gradle")
                .withDocumentationBindings(ref_userguide_scala_plugin: "scala_plugin")
                .withBindings(scalaVersion: libraryVersionProvider.getVersion("scala-library"))
                .withBindings(scalaTestVersion: libraryVersionProvider.getVersion("scalatest_2.10"))
                .withBindings(junitVersion: libraryVersionProvider.getVersion("junit"))
                .create()

        TemplateOperation scalaLibTemplateOperation = templateOperationBuilder.newTemplateOperation()
                .withTemplate("scalalibrary/Library.scala.template")
                .withTarget("src/main/scala/Library.scala")
                .create()

        TemplateOperation scalaTestTemplateOperation = templateOperationBuilder.newTemplateOperation()
                .withTemplate("scalalibrary/LibrarySuite.scala.template")
                .withTarget("src/test/scala/LibrarySuite.scala")
                .create()

        registry.add(BuildInitTypeIds.SCALA_LIBRARY,
                new TemplateBasedProjectInitDescriptor(settingsTemplateOperation,
                        scalalibBuildFileTemplateOperation,
                        new ConditionalTemplateOperation({ fileResolver.resolveFilesAsTree("src/main/scala").empty || fileResolver.resolveFilesAsTree("src/main/scala").empty },
                                scalaLibTemplateOperation, scalaTestTemplateOperation)))

        TemplateOperation groovylibBuildFileTemplateOperation = templateOperationBuilder.newTemplateOperation()
                .withTemplate("groovylibrary/build.gradle.template")
                .withTarget("build.gradle")
                .withDocumentationBindings(ref_userguide_groovy_tutorial: "tutorial_groovy_projects")
                .withBindings(groovyVersion: libraryVersionProvider.getVersion("groovy"))
                .withBindings(junitVersion: libraryVersionProvider.getVersion("junit"))
                .create()

        TemplateOperation groovyLibTemplateOperation = templateOperationBuilder.newTemplateOperation()
                .withTemplate("groovylibrary/Library.groovy.template")
                .withTarget("src/main/groovy/Library.groovy")
                .create()

        TemplateOperation groovyTestTemplateOperation = templateOperationBuilder.newTemplateOperation()
                .withTemplate("groovylibrary/LibraryTest.groovy.template")
                .withTarget("src/test/groovy/LibraryTest.groovy")
                .create()

        registry.add(BuildInitTypeIds.GROOVY_LIBRARY,
                new TemplateBasedProjectInitDescriptor(settingsTemplateOperation,
                        groovylibBuildFileTemplateOperation,
                        new ConditionalTemplateOperation({ fileResolver.resolveFilesAsTree("src/main/groovy").empty || fileResolver.resolveFilesAsTree("src/main/groovy").empty },
                                groovyLibTemplateOperation, groovyTestTemplateOperation)))


        TemplateOperation basicBuildFile  = templateOperationBuilder.newTemplateOperation()
                .withTemplate("build.gradle.template")
                .withTarget("build.gradle")
                .withDocumentationBindings(ref_userguide_java_tutorial: "tutorial_java_projects")
                .create()

        registry.add(BuildInitTypeIds.BASIC, new TemplateBasedProjectInitDescriptor(settingsTemplateOperation, basicBuildFile));
        registry.add(BuildInitTypeIds.POM, new PomProjectInitDescriptor(fileResolver, mavenSettingsProvider))
        return registry
    }

    public ProjectLayoutSetupRegistryFactory(MavenSettingsProvider mavenSettingsProvider,
                                             DocumentationRegistry documentationRegistry,
                                             FileResolver fileResolver) {
        this.mavenSettingsProvider = mavenSettingsProvider
        this.documentationRegistry = documentationRegistry
        this.fileResolver = fileResolver
    }

}
