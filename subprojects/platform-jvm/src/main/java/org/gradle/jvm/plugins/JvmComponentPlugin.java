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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.*;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.JarBinarySpec;
import org.gradle.jvm.JvmComponentExtension;
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.internal.*;
import org.gradle.jvm.internal.plugins.DefaultJvmComponentExtension;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.toolchain.JavaToolChainRegistry;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolChainRegistry;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.*;
import org.gradle.platform.base.internal.toolchain.ToolResolver;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Base plugin for JVM component support. Applies the {@link org.gradle.language.base.plugins.ComponentModelBasePlugin}. Registers the {@link org.gradle.jvm.JvmLibrarySpec} library type for
 * the {@link org.gradle.platform.base.ComponentSpecContainer}.
 */
@Incubating
@SuppressWarnings("UnusedDeclaration")
public class JvmComponentPlugin extends RuleSource {
    @ComponentType
    void register(ComponentTypeBuilder<JvmLibrarySpec> builder) {
        builder.defaultImplementation(DefaultJvmLibrarySpec.class);
    }

    @BinaryType
    void registerJar(BinaryTypeBuilder<JarBinarySpec> builder) {
        builder.defaultImplementation(DefaultJarBinarySpec.class);
    }

    @Model
    JvmComponentExtension jvm(ServiceRegistry serviceRegistry) {
        final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
        return instantiator.newInstance(DefaultJvmComponentExtension.class);
    }

    @Model
    BinaryNamingSchemeBuilder binaryNamingSchemeBuilder() {
        return new DefaultBinaryNamingSchemeBuilder();
    }

    @Model
    JavaToolChainRegistry javaToolChain(ServiceRegistry serviceRegistry) {
        JavaToolChainInternal toolChain = serviceRegistry.get(JavaToolChainInternal.class);
        return new DefaultJavaToolChainRegistry(toolChain);
    }

    @Mutate
    public void registerPlatformResolver(PlatformResolvers platformResolvers) {
        platformResolvers.register(new JavaPlatformResolver());
    }

    @ComponentBinaries
    public void createBinaries(CollectionBuilder<JarBinarySpec> binaries, final JvmLibrarySpec jvmLibrary,
                               PlatformResolvers platforms, BinaryNamingSchemeBuilder namingSchemeBuilder, final JvmComponentExtension jvmComponentExtension,
                               @Path("buildDir") File buildDir, ServiceRegistry serviceRegistry, JavaToolChainRegistry toolChains) {

        final File binariesDir = new File(buildDir, "jars");
        final File classesDir = new File(buildDir, "classes");
        ToolResolver toolResolver = serviceRegistry.get(ToolResolver.class);

        List<JavaPlatform> selectedPlatforms = resolvePlatforms(jvmLibrary, platforms);
        for (final JavaPlatform platform : selectedPlatforms) {
            final JavaToolChainInternal toolChain = (JavaToolChainInternal) toolChains.getForPlatform(platform);
            final String binaryName = createBinaryName(jvmLibrary, namingSchemeBuilder, selectedPlatforms, platform);
            binaries.create(binaryName, new ConfigureJarBinary(jvmLibrary, toolChain, platform, classesDir, binariesDir, jvmComponentExtension, toolResolver));
        }
    }

    private List<JavaPlatform> resolvePlatforms(JvmLibrarySpec jvmLibrary, final PlatformResolvers platforms) {
        List<PlatformRequirement> targetPlatforms = ((JvmLibrarySpecInternal) jvmLibrary).getTargetPlatforms();
        if (targetPlatforms.isEmpty()) {
            // TODO:DAZ Make it simpler to get the default java platform name, or use a spec here
            String defaultJavaPlatformName = new DefaultJavaPlatform(JavaVersion.current()).getName();
            targetPlatforms = Collections.singletonList(DefaultPlatformRequirement.create(defaultJavaPlatformName));
        }
        return CollectionUtils.collect(targetPlatforms, new Transformer<JavaPlatform, PlatformRequirement>() {
            @Override
            public JavaPlatform transform(PlatformRequirement platformRequirement) {
                return platforms.resolve(JavaPlatform.class, platformRequirement);
            }
        });
    }

    @BinaryTasks
    public void createTasks(CollectionBuilder<Task> tasks, final JarBinarySpec binary) {
        String taskName = "create" + StringUtils.capitalize(binary.getName());
        tasks.create(taskName, Jar.class, new Action<Jar>() {
            @Override
            public void execute(Jar jar) {
                jar.setDescription(String.format("Creates the binary file for %s.", binary));
                jar.from(binary.getClassesDir());
                jar.from(binary.getResourcesDir());

                jar.setDestinationDir(binary.getJarFile().getParentFile());
                jar.setArchiveName(binary.getJarFile().getName());
            }
        });

        // bad, bad, bad
        binary.builtBy(tasks.get(taskName));
    }

    private String createBinaryName(JvmLibrarySpec jvmLibrary, BinaryNamingSchemeBuilder namingSchemeBuilder, List<JavaPlatform> selectedPlatforms, JavaPlatform platform) {
        BinaryNamingSchemeBuilder componentBuilder = namingSchemeBuilder
                .withComponentName(jvmLibrary.getName())
                .withTypeString("jar");
        if (selectedPlatforms.size() > 1) {
            componentBuilder = componentBuilder.withVariantDimension(platform.getName());
        }
        return componentBuilder.build().getLifecycleTaskName();
    }

    private static class ConfigureJarBinary implements Action<JarBinarySpec> {
        private final JvmLibrarySpec jvmLibrary;
        private final JavaToolChainInternal toolChain;
        private final JavaPlatform platform;
        private final File classesDir;
        private final File binariesDir;
        private final JvmComponentExtension jvmComponentExtension;
        private final ToolResolver toolResolver;

        public ConfigureJarBinary(JvmLibrarySpec jvmLibrary, JavaToolChainInternal toolChain, JavaPlatform platform, File classesDir, File binariesDir, JvmComponentExtension jvmComponentExtension, ToolResolver toolResolver) {
            this.jvmLibrary = jvmLibrary;
            this.toolChain = toolChain;
            this.platform = platform;
            this.classesDir = classesDir;
            this.binariesDir = binariesDir;
            this.jvmComponentExtension = jvmComponentExtension;
            this.toolResolver = toolResolver;
        }

        public void execute(JarBinarySpec jarBinary) {
            JarBinarySpecInternal jarBinaryInternal = (JarBinarySpecInternal) jarBinary;
            jarBinaryInternal.setBaseName(jvmLibrary.getName());
            jarBinary.setToolChain(toolChain);
            jarBinary.setTargetPlatform(platform);
            jarBinary.setToolResolver(toolResolver);

            File outputDir = new File(classesDir, jarBinary.getName());
            jarBinary.setClassesDir(outputDir);
            jarBinary.setResourcesDir(outputDir);
            jarBinary.setJarFile(new File(binariesDir, String.format("%s/%s.jar", jarBinary.getName(), jarBinaryInternal.getBaseName())));

            jvmComponentExtension.getAllBinariesAction().execute(jarBinary);
        }
    }
}
