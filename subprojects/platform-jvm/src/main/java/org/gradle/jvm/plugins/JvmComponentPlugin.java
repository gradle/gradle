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
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.tasks.Copy;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.JarBinarySpec;
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.internal.*;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.tasks.api.ApiJar;
import org.gradle.jvm.toolchain.JavaToolChainRegistry;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolChainRegistry;
import org.gradle.language.base.internal.ProjectLayout;
import org.gradle.model.*;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.*;
import org.gradle.util.CollectionUtils;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.apache.commons.lang.StringUtils.capitalize;

/**
 * Base plugin for JVM component support. Applies the
 * {@link org.gradle.language.base.plugins.ComponentModelBasePlugin}.
 * Registers the {@link JvmLibrarySpec} library type for the components container.
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
        public void register(ComponentTypeBuilder<JvmLibrarySpec> builder) {
            builder.defaultImplementation(DefaultJvmLibrarySpec.class);
            builder.internalView(JvmLibrarySpecInternal.class);
        }

        @BinaryType
        public void registerJar(BinaryTypeBuilder<JarBinarySpec> builder) {
            builder.defaultImplementation(DefaultJarBinarySpec.class);
            builder.internalView(JarBinarySpecInternal.class);
        }

        @Model
        public JavaToolChainRegistry javaToolChain(ServiceRegistry serviceRegistry) {
            JavaToolChainInternal toolChain = serviceRegistry.get(JavaToolChainInternal.class);
            return new DefaultJavaToolChainRegistry(toolChain);
        }

        @Model
        public ProjectLayout projectLayout(ProjectIdentifier projectIdentifier, @Path("buildDir") File buildDir) {
            return new ProjectLayout(projectIdentifier, buildDir);
        }

        @Mutate
        public void registerPlatformResolver(PlatformResolvers platformResolvers) {
            platformResolvers.register(new JavaPlatformResolver());
        }

        @ComponentBinaries
        public void createBinaries(ModelMap<JarBinarySpec> binaries, PlatformResolvers platforms, final JvmLibrarySpecInternal jvmLibrary) {
            List<JavaPlatform> selectedPlatforms = resolvePlatforms(platforms, jvmLibrary);
            final Set<String> exportedPackages = exportedPackagesOf(jvmLibrary);
            final Collection<DependencySpec> apiDependencies = apiDependenciesOf(jvmLibrary);
            final Collection<DependencySpec> dependencies = componentDependenciesOf(jvmLibrary);
            for (final JavaPlatform platform : selectedPlatforms) {
                final BinaryNamingScheme namingScheme = namingSchemeFor(jvmLibrary, selectedPlatforms, platform);
                binaries.create(namingScheme.getBinaryName(), new Action<JarBinarySpec>() {
                    @Override
                    public void execute(JarBinarySpec jarBinarySpec) {
                        JarBinarySpecInternal jarBinary = (JarBinarySpecInternal) jarBinarySpec;
                        jarBinary.setNamingScheme(namingScheme);
                        jarBinary.setTargetPlatform(platform);
                        jarBinary.setExportedPackages(exportedPackages);
                        jarBinary.setApiDependencies(apiDependencies);
                        jarBinary.setDependencies(dependencies);
                    }
                });
            }
        }

        private List<JavaPlatform> resolvePlatforms(final PlatformResolvers platformResolver,
                                                    JvmLibrarySpecInternal jvmLibrarySpec) {
            List<PlatformRequirement> targetPlatforms = jvmLibrarySpec.getTargetPlatforms();
            if (targetPlatforms.isEmpty()) {
                targetPlatforms = Collections.singletonList(
                    DefaultPlatformRequirement.create(DefaultJavaPlatform.current().getName()));
            }
            return CollectionUtils.collect(targetPlatforms, new Transformer<JavaPlatform, PlatformRequirement>() {
                @Override
                public JavaPlatform transform(PlatformRequirement platformRequirement) {
                    return platformResolver.resolve(JavaPlatform.class, platformRequirement);
                }
            });
        }

        private static Set<String> exportedPackagesOf(JvmLibrarySpecInternal jvmLibrary) {
            return jvmLibrary.getApi().getExports();
        }

        private static Collection<DependencySpec> apiDependenciesOf(JvmLibrarySpecInternal jvmLibrary) {
            return jvmLibrary.getApi().getDependencies().getDependencies();
        }

        private static Collection<DependencySpec> componentDependenciesOf(JvmLibrarySpecInternal jvmLibrary) {
            return jvmLibrary.getDependencies().getDependencies();
        }

        private BinaryNamingScheme namingSchemeFor(JvmLibrarySpec jvmLibrary, List<JavaPlatform> selectedPlatforms, JavaPlatform platform) {
            return DefaultBinaryNamingScheme.component(jvmLibrary.getName())
                    .withBinaryType("Jar")
                    .withRole("jar", true)
                    .withVariantDimension(platform, selectedPlatforms);
        }

        @BinaryTasks
        public void createTasks(ModelMap<Task> tasks, final JarBinarySpecInternal binary, final @Path("buildDir") File buildDir) {
            final File runtimeClassesDir = binary.getClassesDir();
            final File resourcesDir = binary.getResourcesDir();
            final File runtimeJarDestDir = binary.getJarFile().getParentFile();
            final String runtimeJarArchiveName = binary.getJarFile().getName();
            final String createRuntimeJar = "create" + capitalize(binary.getProjectScopedName());
            tasks.create(createRuntimeJar, Jar.class, new Action<Jar>() {
                @Override
                public void execute(Jar jar) {
                    jar.setDescription(String.format("Creates the binary file for %s.", binary));
                    jar.from(runtimeClassesDir);
                    jar.from(resourcesDir);
                    jar.setDestinationDir(runtimeJarDestDir);
                    jar.setArchiveName(runtimeJarArchiveName);
                }
            });

            final JarFile apiJar = binary.getApiJar();
            final Set<String> exportedPackages = binary.getExportedPackages();
            String apiJarTaskName = apiJarTaskName(binary);
            if (exportedPackages.isEmpty()) {
                tasks.create(apiJarTaskName, Copy.class, new Action<Copy>() {
                    @Override
                    public void execute(Copy copy) {
                        copy.setDescription(String.format("Creates the API binary file for %s.", binary));
                        copy.from(new File(runtimeJarDestDir, runtimeJarArchiveName));
                        copy.setDestinationDir(apiJar.getFile().getParentFile());
                        copy.dependsOn(createRuntimeJar);
                        apiJar.setBuildTask(copy);
                    }
                });
            } else {
                tasks.create(apiJarTaskName, ApiJar.class, new Action<ApiJar>() {
                    @Override
                    public void execute(ApiJar jar) {
                        final File apiClassesDir = binary.getNamingScheme().getOutputDirectory(buildDir, "apiClasses");
                        jar.setDescription(String.format("Creates the API binary file for %s.", binary));
                        jar.setRuntimeClassesDir(runtimeClassesDir);
                        jar.setExportedPackages(exportedPackages);
                        jar.setApiClassesDir(apiClassesDir);
                        jar.setDestinationDir(apiJar.getFile().getParentFile());
                        jar.setArchiveName(apiJar.getFile().getName());
                        apiJar.setBuildTask(jar);
                    }
                });
            }
        }

        private String apiJarTaskName(JarBinarySpecInternal binary) {
            String binaryName = binary.getProjectScopedName();
            String libName = binaryName.endsWith("Jar")
                    ? binaryName.substring(0, binaryName.length() - 3)
                    : binaryName;
            return libName + "ApiJar";
        }
    }
}
