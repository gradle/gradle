/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.runtime.jvm.plugins;

import org.gradle.api.*;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.base.ComponentSpecContainer;
import org.gradle.runtime.base.ComponentSpecIdentifier;
import org.gradle.runtime.base.internal.BinaryNamingScheme;
import org.gradle.runtime.base.internal.BinaryNamingSchemeBuilder;
import org.gradle.runtime.base.internal.DefaultBinaryNamingSchemeBuilder;
import org.gradle.runtime.base.internal.DefaultComponentSpecIdentifier;
import org.gradle.runtime.jvm.JvmLibrarySpec;
import org.gradle.runtime.jvm.internal.DefaultJvmLibrarySpec;
import org.gradle.runtime.jvm.internal.DefaultJarBinarySpec;
import org.gradle.runtime.jvm.internal.JarBinarySpecInternal;
import org.gradle.runtime.jvm.internal.plugins.DefaultJvmComponentExtension;
import org.gradle.runtime.jvm.toolchain.JavaToolChain;

import java.io.File;

/**
 * Base plugin for JVM component support. Applies the {@link org.gradle.language.base.plugins.ComponentModelBasePlugin}. Registers the {@link org.gradle.runtime.jvm.JvmLibrarySpec} library type for
 * the {@link org.gradle.runtime.base.ComponentSpecContainer}.
 */
@Incubating
public class JvmComponentPlugin implements Plugin<Project> {

    public void apply(final Project project) {
        project.getPlugins().apply(ComponentModelBasePlugin.class);

        ComponentSpecContainer componentSpecs = project.getExtensions().getByType(ComponentSpecContainer.class);

        final ProjectSourceSet sources = project.getExtensions().getByType(ProjectSourceSet.class);
        componentSpecs.registerFactory(JvmLibrarySpec.class, new NamedDomainObjectFactory<JvmLibrarySpec>() {
            public JvmLibrarySpec create(String name) {
                ComponentSpecIdentifier id = new DefaultComponentSpecIdentifier(project.getPath(), name);
                return new DefaultJvmLibrarySpec(id, sources.maybeCreate(name));
            }
        });

        final NamedDomainObjectContainer<JvmLibrarySpec> jvmLibraries = componentSpecs.containerWithType(JvmLibrarySpec.class);
        project.getExtensions().create("jvm", DefaultJvmComponentExtension.class, jvmLibraries);
    }

    /**
     * Model rules.
     */
    @SuppressWarnings("UnusedDeclaration")
    @RuleSource
    public static class Rules {

        @Model("jvm.libraries")
        NamedDomainObjectCollection<JvmLibrarySpec> jvmLibraries(ComponentSpecContainer components) {
            return components.withType(JvmLibrarySpec.class);
        }

        @Model
        BinaryNamingSchemeBuilder binaryNamingSchemeBuilder() {
            return new DefaultBinaryNamingSchemeBuilder();
        }

        @Mutate
        public void createBinaries(BinaryContainer binaries, BinaryNamingSchemeBuilder namingSchemeBuilder, NamedDomainObjectCollection<JvmLibrarySpec> libraries, @Path("buildDir") File buildDir, ServiceRegistry serviceRegistry) {
            JavaToolChain toolChain = serviceRegistry.get(JavaToolChain.class);
            for (JvmLibrarySpec jvmLibrary : libraries) {
                BinaryNamingScheme namingScheme = namingSchemeBuilder
                        .withComponentName(jvmLibrary.getName())
                        .withTypeString("jar")
                        .build();
                JarBinarySpecInternal jarBinary = new DefaultJarBinarySpec(jvmLibrary, namingScheme, toolChain);
                jarBinary.source(jvmLibrary.getSource());
                configureBinaryOutputLocations(jarBinary, buildDir);
                jvmLibrary.getBinaries().add(jarBinary);
                binaries.add(jarBinary);
            }
        }

        private void configureBinaryOutputLocations(JarBinarySpecInternal jarBinary, File buildDir) {
            File binariesDir = new File(buildDir, "jars");
            File classesDir = new File(buildDir, "classes");

            String outputBaseName = jarBinary.getNamingScheme().getOutputDirectoryBase();
            File outputDir = new File(classesDir, outputBaseName);
            jarBinary.setClassesDir(outputDir);
            jarBinary.setResourcesDir(outputDir);
            jarBinary.setJarFile(new File(binariesDir, String.format("%s/%s.jar", outputBaseName, jarBinary.getLibrary().getName())));
        }


        @Mutate
        public void createTasks(TaskContainer tasks, BinaryContainer binaries) {
            for (JarBinarySpecInternal projectJarBinary : binaries.withType(JarBinarySpecInternal.class)) {
                Task jarTask = createJarTask(tasks, projectJarBinary);
                projectJarBinary.builtBy(jarTask);
                projectJarBinary.getTasks().add(jarTask);
            }
        }

        private Task createJarTask(TaskContainer tasks, JarBinarySpecInternal binary) {
            Jar jar = tasks.create(binary.getNamingScheme().getTaskName("create"), Jar.class);
            jar.setDescription(String.format("Creates the binary file for %s.", binary.getNamingScheme().getDescription()));
            jar.from(binary.getClassesDir());
            jar.from(binary.getResourcesDir());

            jar.setDestinationDir(binary.getJarFile().getParentFile());
            jar.setArchiveName(binary.getJarFile().getName());

            return jar;
        }
    }
}
