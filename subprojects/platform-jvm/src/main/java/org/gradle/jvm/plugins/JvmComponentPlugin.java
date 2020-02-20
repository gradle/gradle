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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.JarBinarySpec;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.internal.DefaultJarBinarySpec;
import org.gradle.jvm.internal.DefaultJvmBinarySpec;
import org.gradle.jvm.internal.DefaultJvmLibrarySpec;
import org.gradle.jvm.internal.JarBinarySpecInternal;
import org.gradle.jvm.internal.JarFile;
import org.gradle.jvm.internal.JavaPlatformResolver;
import org.gradle.jvm.internal.JvmAssembly;
import org.gradle.jvm.internal.JvmBinarySpecInternal;
import org.gradle.jvm.internal.JvmLibrarySpecInternal;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.tasks.api.ApiJar;
import org.gradle.jvm.toolchain.JavaToolChainRegistry;
import org.gradle.jvm.toolchain.LocalJava;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolChainRegistry;
import org.gradle.jvm.toolchain.internal.InstalledJdk;
import org.gradle.jvm.toolchain.internal.InstalledJdkInternal;
import org.gradle.jvm.toolchain.internal.InstalledJre;
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe;
import org.gradle.jvm.toolchain.internal.LocalJavaInstallation;
import org.gradle.language.base.internal.ProjectLayout;
import org.gradle.model.Defaults;
import org.gradle.model.Each;
import org.gradle.model.Model;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.Hidden;
import org.gradle.platform.base.BinaryTasks;
import org.gradle.platform.base.ComponentBinaries;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.InvalidModelException;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.DefaultBinaryNamingScheme;
import org.gradle.platform.base.internal.DefaultPlatformRequirement;
import org.gradle.platform.base.internal.PlatformRequirement;
import org.gradle.platform.base.internal.PlatformResolvers;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.apache.commons.lang.StringUtils.capitalize;

/**
 * Base plugin for JVM component support. Applies the {@link org.gradle.language.base.plugins.ComponentModelBasePlugin}. Registers the {@link JvmLibrarySpec} library type for the components
 * container.
 */
@Incubating
@Deprecated
public class JvmComponentPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        DeprecationLogger.deprecatePlugin("jvm-component")
            .willBeRemovedInGradle7()
            .withUpgradeGuideSection(6, "upgrading_jvm_plugins")
            .nagUser();
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @ComponentType
        public void register(TypeBuilder<JvmLibrarySpec> builder) {
            builder.defaultImplementation(DefaultJvmLibrarySpec.class);
            builder.internalView(JvmLibrarySpecInternal.class);
        }

        @ComponentType
        public void registerJvmBinarySpec(TypeBuilder<JvmBinarySpec> builder) {
            builder.defaultImplementation(DefaultJvmBinarySpec.class);
            builder.internalView(JvmBinarySpecInternal.class);
        }

        @ComponentType
        public void registerJarBinarySpec(TypeBuilder<JarBinarySpec> builder) {
            builder.defaultImplementation(DefaultJarBinarySpec.class);
            builder.internalView(JarBinarySpecInternal.class);
        }

        @Model
        @Hidden
        public JavaToolChainRegistry javaToolChain(ServiceRegistry serviceRegistry) {
            JavaToolChainInternal toolChain = serviceRegistry.get(JavaToolChainInternal.class);
            return new DefaultJavaToolChainRegistry(toolChain);
        }

        @Model
        public void javaInstallations(ModelMap<LocalJava> jdks) {
        }

        @Model
        @Hidden
        public void javaToolChains(ModelMap<LocalJavaInstallation> javaInstallations, final JavaInstallationProbe probe) {
            javaInstallations.create("currentGradleJDK", InstalledJdk.class, new Action<InstalledJdk>() {
                @Override
                public void execute(InstalledJdk installedJdk) {
                    installedJdk.setJavaHome(Jvm.current().getJavaHome());
                    probe.current().configure(installedJdk);
                }
            });
        }

        private static void validateNoDuplicate(ModelMap<LocalJava> jdks) {
            ListMultimap<String, LocalJava> jdksByPath = indexByPath(jdks);
            List<String> errors = Lists.newArrayList();
            for (String path : jdksByPath.keySet()) {
                checkDuplicateForPath(jdksByPath, path, errors);
            }
            if (!errors.isEmpty()) {
                throw new InvalidModelException(String.format("Duplicate Java installation found:\n%s", Joiner.on("\n").join(errors)));
            }
        }

        @Mutate
        public void registerPlatformResolver(PlatformResolvers platformResolvers) {
            platformResolvers.register(new JavaPlatformResolver());
        }

        @Model
        @Hidden
        JavaInstallationProbe javaInstallationProbe(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(JavaInstallationProbe.class);
        }

        @Defaults
        public void resolveJavaToolChains(final ModelMap<LocalJavaInstallation> installedJdks, ModelMap<LocalJava> localJavaInstalls, final JavaInstallationProbe probe) {
            File currentJavaHome = canonicalFile(Jvm.current().getJavaHome());
            // TODO:Cedric The following validation should in theory happen in its own rule, but it is not possible now because
            // there's no way to iterate on the map as subject of a `@Validate` rule without Gradle thinking you're trying to mutate it
            validateNoDuplicate(localJavaInstalls);
            for (final LocalJava candidate : localJavaInstalls) {
                final File javaHome = canonicalFile(candidate.getPath());
                final JavaInstallationProbe.ProbeResult probeResult = probe.checkJdk(javaHome);
                Class<? extends LocalJavaInstallation> clazz;
                switch (probeResult.getInstallType()) {
                    case IS_JDK:
                        clazz = InstalledJdkInternal.class;
                        break;
                    case IS_JRE:
                        clazz = InstalledJre.class;
                        break;
                    case NO_SUCH_DIRECTORY:
                        throw new InvalidModelException(String.format("Path to JDK '%s' doesn't exist: %s", candidate.getName(), javaHome));
                    case INVALID_JDK:
                    default:
                        throw new InvalidModelException(String.format("JDK '%s' is not a valid JDK installation: %s\n%s", candidate.getName(), javaHome, probeResult.getError()));
                }

                if (!javaHome.equals(currentJavaHome)) {
                    installedJdks.create(candidate.getName(), clazz, new Action<LocalJavaInstallation>() {
                        @Override
                        public void execute(LocalJavaInstallation installedJdk) {
                            installedJdk.setJavaHome(javaHome);
                            probeResult.configure(installedJdk);
                        }
                    });
                }
            }
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

        private static File canonicalFile(File f) {
            try {
                return f.getCanonicalFile();
            } catch (IOException e) {
                return f;
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
            final File runtimeJarDestDir = binary.getJarFile().getParentFile();
            final String runtimeJarArchiveName = binary.getJarFile().getName();
            final String createRuntimeJar = "create" + capitalize(binary.getProjectScopedName());
            final JvmAssembly assembly = binary.getAssembly();
            final JarFile runtimeJarFile = binary.getRuntimeJar();
            tasks.create(createRuntimeJar, Jar.class, new Action<Jar>() {
                @Override
                public void execute(Jar jar) {
                    jar.setDescription("Creates the binary file for " + binary + ".");
                    jar.from(assembly.getClassDirectories());
                    jar.from(assembly.getResourceDirectories());
                    jar.getDestinationDirectory().set(runtimeJarDestDir);
                    jar.getArchiveFileName().set(runtimeJarArchiveName);
                    jar.dependsOn(assembly);
                    runtimeJarFile.setBuildTask(jar);
                }
            });

            final JarFile apiJarFile = binary.getApiJar();
            final Set<String> exportedPackages = binary.getExportedPackages();
            String apiJarTaskName = apiJarTaskName(binary);
            tasks.create(apiJarTaskName, ApiJar.class, new Action<ApiJar>() {
                @Override
                public void execute(ApiJar apiJarTask) {
                    apiJarTask.setDescription("Creates the API binary file for " + binary + ".");
                    apiJarTask.setOutputFile(apiJarFile.getFile());
                    apiJarTask.setExportedPackages(exportedPackages);
                    apiJarTask.source(assembly.getClassDirectories());
                    apiJarTask.dependsOn(assembly);
                    apiJarFile.setBuildTask(apiJarTask);
                }
            });
        }

        private String apiJarTaskName(JarBinarySpecInternal binary) {
            String binaryName = binary.getProjectScopedName();
            String libName = binaryName.endsWith("Jar")
                ? binaryName.substring(0, binaryName.length() - 3)
                : binaryName;
            return libName + "ApiJar";
        }

        private static void checkDuplicateForPath(ListMultimap<String, LocalJava> index, String path, List<String> errors) {
            List<LocalJava> localJavas = index.get(path);
            if (localJavas.size() > 1) {
                errors.add(String.format("   - %s are both pointing to the same JDK installation path: %s",
                    Joiner.on(", ").join(Iterables.transform(localJavas, new Function<LocalJava, String>() {
                        @Override
                        public String apply(LocalJava input) {
                            return "'" + input.getName() + "'";
                        }
                    })), path));
            }
        }

        private static ListMultimap<String, LocalJava> indexByPath(ModelMap<LocalJava> localJavaInstalls) {
            final ListMultimap<String, LocalJava> index = ArrayListMultimap.create();
            for (LocalJava localJava : localJavaInstalls) {
                try {
                    index.put(localJava.getPath().getCanonicalPath(), localJava);
                } catch (IOException e) {
                    // ignore this installation for validation, it will be caught later
                }
            }
            return index;
        }

        private static List<LocalJava> toImmutableJdkList(ModelMap<LocalJava> jdks) {
            final List<LocalJava> asImmutable = Lists.newArrayList();
            jdks.afterEach(new Action<LocalJava>() {
                @Override
                public void execute(LocalJava localJava) {
                    asImmutable.add(localJava);
                }
            });
            return asImmutable;
        }

        @Defaults
        void configureJvmBinaries(@Each JvmBinarySpecInternal jvmBinary, ProjectLayout projectLayout) {
            File buildDir = projectLayout.getBuildDir();
            BinaryNamingScheme namingScheme = jvmBinary.getNamingScheme();
            jvmBinary.setClassesDir(namingScheme.getOutputDirectory(buildDir, "classes"));
            jvmBinary.setResourcesDir(namingScheme.getOutputDirectory(buildDir, "resources"));
        }

        @Defaults
        void configureJarBinaries(@Each JarBinarySpecInternal jarBinary, ProjectLayout projectLayout, JavaToolChainRegistry toolChains) {
            String libraryName = jarBinary.getId().getLibraryName();
            File jarsDir = jarBinary.getNamingScheme().getOutputDirectory(projectLayout.getBuildDir(), "jars");
            jarBinary.setJarFile(new File(jarsDir, libraryName + ".jar"));
            jarBinary.setApiJarFile(new File(jarsDir, "api/" + libraryName + ".jar"));
            jarBinary.setToolChain(toolChains.getForPlatform(jarBinary.getTargetPlatform()));
        }
    }
}
