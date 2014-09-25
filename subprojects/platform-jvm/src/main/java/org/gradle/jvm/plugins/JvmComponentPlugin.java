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
package org.gradle.jvm.plugins;

import org.gradle.api.*;
import org.gradle.internal.Actions;
import org.gradle.jvm.JarBinarySpec;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.internal.configure.DefaultJarBinariesFactory;
import org.gradle.jvm.internal.configure.JarBinariesFactory;
import org.gradle.jvm.internal.configure.JarBinarySpecInitializer;
import org.gradle.jvm.internal.configure.JvmLibrarySpecInitializer;
import org.gradle.jvm.platform.internal.DefaultJvmPlatform;
import org.gradle.jvm.platform.JvmPlatform;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.*;
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.internal.DefaultJvmLibrarySpec;
import org.gradle.jvm.internal.JarBinarySpecInternal;
import org.gradle.jvm.internal.plugins.DefaultJvmComponentExtension;
import org.gradle.jvm.toolchain.JavaToolChain;

import java.io.File;

/**
 * Base plugin for JVM component support. Applies the {@link org.gradle.language.base.plugins.ComponentModelBasePlugin}. Registers the {@link org.gradle.jvm.JvmLibrarySpec} library type for
 * the {@link org.gradle.platform.base.ComponentSpecContainer}.
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

        @Model
        NamedDomainObjectCollection<JvmLibrarySpec> jvmLibraries(ComponentSpecContainer components) {
            return components.withType(JvmLibrarySpec.class);
        }

        @Model
        BinaryNamingSchemeBuilder binaryNamingSchemeBuilder() {
            return new DefaultBinaryNamingSchemeBuilder();
        }

        @Mutate
        public void registerJvmPlatformFactory(PlatformContainer platforms, ServiceRegistry serviceRegistry) {
            final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            platforms.registerFactory(JvmPlatform.class, new NamedDomainObjectFactory<JvmPlatform>() {
                public JvmPlatform create(String name) {
                    return new DefaultJvmPlatform(name);
                }
            });

            //Create default platforms available for Java
            for (JavaVersion javaVersion: JavaVersion.values()) {
                String name = DefaultJvmPlatform.generateName(javaVersion);
                JvmPlatform platform = platforms.create(name, JvmPlatform.class);
                platform.setTargetCompatibility(javaVersion);
            }
        }

        @Mutate
        public void createBinaries(BinaryContainer binaries, PlatformContainer platforms, BinaryNamingSchemeBuilder namingSchemeBuilder, NamedDomainObjectCollection<JvmLibrarySpec> libraries, @Path("buildDir") File buildDir, ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            JavaToolChain toolChain = serviceRegistry.get(JavaToolChain.class);

            Action<JarBinarySpec> configureBinaryAction = new JarBinarySpecInitializer(buildDir);

            //TODO freekh: we are skipping tools setup for jvm, because we only have one language, and we will want each language to register their own extensions/extra toolchains (using rules?)
            @SuppressWarnings("unchecked") Action<JarBinarySpec> initAction = Actions.composite(configureBinaryAction, new MarkBinariesBuildable());
            JarBinariesFactory factory = new DefaultJarBinariesFactory(instantiator, initAction);

            Action<JvmLibrarySpec> createBinariesAction =
                    new JvmLibrarySpecInitializer(factory, namingSchemeBuilder, toolChain, platforms);

            for (JvmLibrarySpec jvmLibrary : libraries) {
                createBinariesAction.execute(jvmLibrary);
                //TODO freekh: this should be moved into MarkBinariesBuildable
                for (JvmBinarySpec jarBinarySpec: jvmLibrary.getBinaries()){
                    toolChain.assertValidPlatform(jarBinarySpec.getTargetPlatform(), platforms);
                }
                binaries.addAll(jvmLibrary.getBinaries());
            }
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

    private static class MarkBinariesBuildable implements Action<JarBinarySpec> { //TODO: this is exactly the same for both Native and Jvm and should be refactored
        public void execute(JarBinarySpec jarBinarySpec) {
            JavaToolChain toolChain = jarBinarySpec.getToolChain();
            boolean canBuild = toolChain.select(jarBinarySpec.getTargetPlatform());
            ((JarBinarySpecInternal) jarBinarySpec).setBuildable(canBuild);
        }
    }
}
