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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.GUtil;

public class ScalaLibraryProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor{

    public ScalaLibraryProjectInitDescriptor(TemplateOperationFactory templateOperationFactory, final FileResolver fileResolver, TemplateLibraryVersionProvider libraryVersionProvider, TemplateOperation delegate) {
        super("scala", templateOperationFactory, fileResolver);

        register(delegate);
        register(templateOperationFactory.newTemplateOperation()
                .withTemplate("scalalibrary/build.gradle.template")
                .withTarget("build.gradle")
                .withDocumentationBindings(GUtil.map("ref_userguide_scala_plugin", "scala_plugin"))
                .withBindings(GUtil.map("scalaVersion", libraryVersionProvider.getVersion("scala")))
                .withBindings(GUtil.map("scalaLibraryVersion", libraryVersionProvider.getVersion("scala-library")))
                .withBindings(GUtil.map("scalaTestModule", "scalatest_" + libraryVersionProvider.getVersion("scala")))
                .withBindings(GUtil.map("scalaTestVersion", libraryVersionProvider.getVersion("scalatest")))
                .withBindings(GUtil.map("scalaXmlModule", "scala-xml_" + libraryVersionProvider.getVersion("scala")))
                .withBindings(GUtil.map("scalaXmlVersion", libraryVersionProvider.getVersion("scala-xml")))
                .withBindings(GUtil.map("junitVersion", libraryVersionProvider.getVersion("junit")))
                .create()
        );

        TemplateOperation scalaLibTemplateOperation = fromClazzTemplate("scalalibrary/Library.scala.template", "main");
        TemplateOperation scalaTestTemplateOperation = fromClazzTemplate("scalalibrary/LibrarySuite.scala.template", "test");

        register(whenNoSourcesAvailable(scalaLibTemplateOperation, scalaTestTemplateOperation));
    }
}
