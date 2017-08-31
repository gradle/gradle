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

public class ScalaLibraryProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor{

    private final DocumentationRegistry documentationRegistry;

    public ScalaLibraryProjectInitDescriptor(TemplateOperationFactory templateOperationFactory, FileResolver fileResolver,
                                             TemplateLibraryVersionProvider libraryVersionProvider, ProjectInitDescriptor globalSettingsDescriptor, DocumentationRegistry documentationRegistry) {
        super("scala", templateOperationFactory, fileResolver, libraryVersionProvider, globalSettingsDescriptor);
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public void generate(BuildInitTestFramework testFramework) {
        globalSettingsDescriptor.generate(testFramework);

        String scalaVersion = libraryVersionProvider.getVersion("scala");
        String scalaLibraryVersion = libraryVersionProvider.getVersion("scala-library");
        String scalaTestVersion = libraryVersionProvider.getVersion("scalatest");
        String junitVersion = libraryVersionProvider.getVersion("junit");
        String scalaXmlVersion = libraryVersionProvider.getVersion("scala-xml");

        BuildScriptBuilder buildScriptBuilder = new BuildScriptBuilder(fileResolver.resolve("build.gradle"))
            .fileComment("This generated file contains a sample Scala library project to get you started.")
            .fileComment("For more details take a look at the Scala plugin chapter in the Gradle")
            .fileComment("user guide available at " + documentationRegistry.getDocumentationFor("scala_plugin"))
            .plugin("Apply the scala plugin to add support for Scala", "scala")
            .compileDependency("Use Scala " + scalaVersion + " in our library project",
                "org.scala-lang:scala-library:" + scalaLibraryVersion)
            .testCompileDependency("Use Scalatest for testing our library",
                "junit:junit:" + junitVersion,
                "org.scalatest:scalatest_" + scalaVersion + ":" + scalaTestVersion)
            .testRuntimeDependency("Need scala-xml at test runtime",
                "org.scala-lang.modules:scala-xml_" + scalaVersion + ":" + scalaXmlVersion);

        buildScriptBuilder.create().generate();

        TemplateOperation scalaLibTemplateOperation = fromClazzTemplate("scalalibrary/Library.scala.template", "main");
        TemplateOperation scalaTestTemplateOperation = fromClazzTemplate("scalalibrary/LibrarySuite.scala.template", "test");
        whenNoSourcesAvailable(scalaLibTemplateOperation, scalaTestTemplateOperation).generate();
    }

    @Override
    public boolean supports(BuildInitTestFramework testFramework) {
        return false;
    }
}
