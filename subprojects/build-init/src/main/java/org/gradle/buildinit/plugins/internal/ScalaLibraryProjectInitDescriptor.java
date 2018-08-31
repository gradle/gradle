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
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;

import java.util.Collections;
import java.util.Set;

public class ScalaLibraryProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {

    private final DocumentationRegistry documentationRegistry;

    public ScalaLibraryProjectInitDescriptor(BuildScriptBuilderFactory scriptBuilderFactory, TemplateOperationFactory templateOperationFactory, FileResolver fileResolver,
                                             TemplateLibraryVersionProvider libraryVersionProvider, DocumentationRegistry documentationRegistry) {
        super("scala", scriptBuilderFactory, templateOperationFactory, fileResolver, libraryVersionProvider);
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public String getId() {
        return "scala-library";
    }

    @Override
    protected void generate(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        String scalaVersion = libraryVersionProvider.getVersion("scala");
        String scalaLibraryVersion = libraryVersionProvider.getVersion("scala-library");
        String scalaTestVersion = libraryVersionProvider.getVersion("scalatest");
        String junitVersion = libraryVersionProvider.getVersion("junit");
        String scalaXmlVersion = libraryVersionProvider.getVersion("scala-xml");

        buildScriptBuilder
            .fileComment("This generated file contains a sample Scala library project to get you started.")
            .fileComment("For more details take a look at the Scala plugin chapter in the Gradle")
            .fileComment("user guide available at " + documentationRegistry.getDocumentationFor("scala_plugin"))
            .plugin("Apply the scala plugin to add support for Scala", "scala")
            .implementationDependency("Use Scala " + scalaVersion + " in our library project",
                "org.scala-lang:scala-library:" + scalaLibraryVersion)
            .testImplementationDependency("Use Scalatest for testing our library",
                "junit:junit:" + junitVersion,
                "org.scalatest:scalatest_" + scalaVersion + ":" + scalaTestVersion)
            .testRuntimeOnlyDependency("Need scala-xml at test runtime",
                "org.scala-lang.modules:scala-xml_" + scalaVersion + ":" + scalaXmlVersion);

        TemplateOperation scalaLibTemplateOperation = fromClazzTemplate("scalalibrary/Library.scala.template", settings, "main");
        TemplateOperation scalaTestTemplateOperation = fromClazzTemplate("scalalibrary/LibrarySuite.scala.template", settings, "test");
        whenNoSourcesAvailable(scalaLibTemplateOperation, scalaTestTemplateOperation).generate();
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework() {
        return BuildInitTestFramework.SCALATEST;
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks() {
        return Collections.singleton(BuildInitTestFramework.SCALATEST);
    }
}
