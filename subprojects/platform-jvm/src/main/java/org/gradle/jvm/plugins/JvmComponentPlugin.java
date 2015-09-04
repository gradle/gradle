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
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.JarBinarySpec;
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.internal.*;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.toolchain.JavaToolChainRegistry;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolChainRegistry;
import org.gradle.model.*;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.*;
import org.gradle.util.CollectionUtils;

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Base plugin for JVM component support. Applies the {@link org.gradle.language.base.plugins.ComponentModelBasePlugin}. Registers the {@link org.gradle.jvm.JvmLibrarySpec} library type for
 * the components container.
 */
@Incubating
public class JvmComponentPlugin implements Plugin<Project> {
    private final ModelRegistry modelRegistry;

    @Inject
    public JvmComponentPlugin(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    @Override
    public void apply(Project project) {
        modelRegistry.getRoot().applyToAllLinksTransitive(ModelType.of(ComponentSpec.class), JarBinaryRules.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @ComponentType
        void register(ComponentTypeBuilder<JvmLibrarySpec> builder) {
            builder.defaultImplementation(DefaultJvmLibrarySpec.class);
        }

        @BinaryType
        void registerJar(BinaryTypeBuilder<JarBinarySpec> builder) {
            builder.defaultImplementation(DefaultJarBinarySpec.class);
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

        @Model
        BuildDirHolder buildDirHolder(@Path("buildDir") File buildDir) {
            return new BuildDirHolder(buildDir);
        }

        @Mutate
        public void registerPlatformResolver(PlatformResolvers platformResolvers) {
            platformResolvers.register(new JavaPlatformResolver());
        }

        @ComponentBinaries
        public void createBinaries(ModelMap<JarBinarySpec> binaries, final JvmLibrarySpec jvmLibrary,
                                   PlatformResolvers platforms, BinaryNamingSchemeBuilder namingSchemeBuilder,
                                   @Path("buildDir") File buildDir) {
            List<JavaPlatform> selectedPlatforms = resolvePlatforms(jvmLibrary, platforms);
            for (final JavaPlatform platform : selectedPlatforms) {
                final String binaryName = createBinaryName(jvmLibrary, namingSchemeBuilder, selectedPlatforms, platform);
                binaries.create(binaryName, new Action<JarBinarySpec>() {
                    @Override
                    public void execute(JarBinarySpec jarBinary) {
                        jarBinary.setTargetPlatform(platform);
                    }
                });
            }
        }

        private List<JavaPlatform> resolvePlatforms(JvmLibrarySpec jvmLibrary, final PlatformResolvers platforms) {
            List<PlatformRequirement> targetPlatforms = ((JvmLibrarySpecInternal) jvmLibrary).getTargetPlatforms();
            if (targetPlatforms.isEmpty()) {
                targetPlatforms = Collections.singletonList(DefaultPlatformRequirement.create(DefaultJavaPlatform.current().getName()));
            }
            return CollectionUtils.collect(targetPlatforms, new Transformer<JavaPlatform, PlatformRequirement>() {
                @Override
                public JavaPlatform transform(PlatformRequirement platformRequirement) {
                    return platforms.resolve(JavaPlatform.class, platformRequirement);
                }
            });
        }

        @BinaryTasks
        public void createTasks(ModelMap<Task> tasks, final JarBinarySpec binary) {
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
    }
}
