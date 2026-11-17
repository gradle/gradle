/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.buildinit.plugins.internal.services;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.buildinit.plugins.internal.CppApplicationProjectInitDescriptor;
import org.gradle.buildinit.plugins.internal.CppLibraryProjectInitDescriptor;
import org.gradle.buildinit.plugins.internal.GroovyGradlePluginProjectInitDescriptor;
import org.gradle.buildinit.plugins.internal.JavaGradlePluginProjectInitDescriptor;
import org.gradle.buildinit.plugins.internal.JvmApplicationProjectInitDescriptor;
import org.gradle.buildinit.plugins.internal.JvmLibraryProjectInitDescriptor;
import org.gradle.buildinit.plugins.internal.KotlinGradlePluginProjectInitDescriptor;
import org.gradle.buildinit.plugins.internal.ProjectTypeContributor;
import org.gradle.buildinit.plugins.internal.SwiftApplicationProjectInitDescriptor;
import org.gradle.buildinit.plugins.internal.SwiftLibraryProjectInitDescriptor;
import org.gradle.buildinit.plugins.internal.TemplateLibraryVersionProvider;
import org.gradle.buildinit.plugins.internal.TemplateOperationFactory;
import org.gradle.buildinit.plugins.internal.model.Description;
import org.jspecify.annotations.NullMarked;

/**
 * Contributes the project types that live in this module: JVM applications and libraries, Gradle plugins, and C++ and Swift applications and libraries.
 */
@NullMarked
public class BuiltInProjectTypeContributor implements ProjectTypeContributor {
    private final DocumentationRegistry documentationRegistry;

    public BuiltInProjectTypeContributor(DocumentationRegistry documentationRegistry) {
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public void contribute(Registrar registrar) {
        TemplateLibraryVersionProvider libraryVersionProvider = registrar.getLibraryVersionProvider();
        TemplateOperationFactory templateOperationFactory = registrar.getTemplateOperationFactory();
        registrar.registerJvmProjectType(new JvmApplicationProjectInitDescriptor(Description.JAVA, libraryVersionProvider, documentationRegistry));
        registrar.registerJvmProjectType(new JvmLibraryProjectInitDescriptor(Description.JAVA, libraryVersionProvider, documentationRegistry));
        registrar.registerJvmProjectType(new JvmApplicationProjectInitDescriptor(Description.GROOVY, libraryVersionProvider, documentationRegistry));
        registrar.registerJvmProjectType(new JvmLibraryProjectInitDescriptor(Description.GROOVY, libraryVersionProvider, documentationRegistry));
        registrar.registerJvmProjectType(new JvmApplicationProjectInitDescriptor(Description.SCALA, libraryVersionProvider, documentationRegistry));
        registrar.registerJvmProjectType(new JvmLibraryProjectInitDescriptor(Description.SCALA, libraryVersionProvider, documentationRegistry));
        registrar.registerJvmProjectType(new JvmApplicationProjectInitDescriptor(Description.KOTLIN, libraryVersionProvider, documentationRegistry));
        registrar.registerJvmProjectType(new JvmLibraryProjectInitDescriptor(Description.KOTLIN, libraryVersionProvider, documentationRegistry));
        registrar.registerProjectType(new CppApplicationProjectInitDescriptor(templateOperationFactory, documentationRegistry));
        registrar.registerProjectType(new CppLibraryProjectInitDescriptor(templateOperationFactory, documentationRegistry));
        registrar.registerJvmProjectType(new JavaGradlePluginProjectInitDescriptor(libraryVersionProvider, documentationRegistry));
        registrar.registerJvmProjectType(new GroovyGradlePluginProjectInitDescriptor(libraryVersionProvider, documentationRegistry));
        registrar.registerJvmProjectType(new KotlinGradlePluginProjectInitDescriptor(libraryVersionProvider, documentationRegistry));
        registrar.registerProjectType(new SwiftApplicationProjectInitDescriptor(templateOperationFactory, documentationRegistry));
        registrar.registerProjectType(new SwiftLibraryProjectInitDescriptor(templateOperationFactory, documentationRegistry));
    }
}
