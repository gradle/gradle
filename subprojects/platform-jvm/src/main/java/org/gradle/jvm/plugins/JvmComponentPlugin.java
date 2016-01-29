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
import com.google.common.collect.*;
import org.gradle.api.*;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.JarBinarySpec;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.internal.*;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.tasks.api.ApiJar;
import org.gradle.jvm.toolchain.JavaToolChainRegistry;
import org.gradle.jvm.toolchain.JdkSpec;
import org.gradle.jvm.toolchain.internal.*;
import org.gradle.language.base.internal.ProjectLayout;
import org.gradle.model.*;
import org.gradle.model.internal.core.Hidden;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.*;
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
public class JvmComponentPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @ComponentType
        public void register(ComponentTypeBuilder<JvmLibrarySpec> builder) {
            builder.defaultImplementation(DefaultJvmLibrarySpec.class);
            builder.internalView(JvmLibrarySpecInternal.class);
        }

        @BinaryType
        public void registerJvmBinarySpec(BinaryTypeBuilder<JvmBinarySpec> builder) {
            builder.defaultImplementation(DefaultJvmBinarySpec.class);
            builder.internalView(JvmBinarySpecInternal.class);
        }

        @BinaryType
        public void registerJarBinarySpec(BinaryTypeBuilder<JarBinarySpec> builder) {
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

        @Model
        public void jdks(ModelMap<JdkSpec> jdks) {
        }

        @Model
        public void installedJdks(ModelMap<InstalledJdk> installedJdks, final JavaInstallationProbe probe) {
            installedJdks.create("currentGradleJDK", InstalledJdk.class, new Action<InstalledJdk>() {
                @Override
                public void execute(InstalledJdk installedJdk) {
                    installedJdk.setJavaHome(Jvm.current().getJavaHome());
                    probe.current(installedJdk);
                }
            });
        }

        @Model
        public void installedJres(ModelMap<InstalledJre> installedJres, final JavaInstallationProbe probe) {
        }

        @Validate
        public void validateJDKs(ModelMap<JdkSpec> jdks) {
            ImmutableListMultimap<String, JdkSpec> jdksByPath = indexByPath(jdks);
            List<String> errors = Lists.newArrayList();
            for (String path : jdksByPath.keySet()) {
                checkDuplicateForPath(jdksByPath, path, errors);
            }
            if (!errors.isEmpty()) {
                throw new InvalidModelException(String.format("Duplicate JDK declared:\n%s", Joiner.on("\n").join(errors)));
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
        public void resolveJDKs(final ModelMap<InstalledJdk> installedJdks, ModelMap<JdkSpec> jdks, final JavaInstallationProbe probe) {
            resolveJavaInstall(installedJdks, jdks, probe, InstalledJdk.class);
        }

        @Defaults
        public void resolveJREs(final ModelMap<InstalledJre> installedJdks, ModelMap<JdkSpec> jdks, final JavaInstallationProbe probe) {
            resolveJavaInstall(installedJdks, jdks, probe, InstalledJre.class);
        }

        private static <T extends LocalJavaInstallation> void resolveJavaInstall(final ModelMap<T> installed, ModelMap<JdkSpec> jdks, final JavaInstallationProbe probe, final Class<T> clazz) {
            File currentJavaHome = canonicalFile(Jvm.current().getJavaHome());
            for (final JdkSpec jdk : jdks) {
                final File javaHome = canonicalFile(jdk.getPath());
                JavaInstallationProbe.ProbeResult probeResult = probe.checkJdk(javaHome);
                JavaInstallationProbe.ProbeResult kind = clazz == InstalledJdk.class ? JavaInstallationProbe.ProbeResult.IS_JDK : JavaInstallationProbe.ProbeResult.IS_JRE;
                if (probeResult == kind) {
                    if (!javaHome.equals(currentJavaHome)) {
                        installed.create(jdk.getName(), clazz, new Action<T>() {
                            @Override
                            public void execute(T installedJdk) {
                                installedJdk.setJavaHome(javaHome);
                                probe.configure(javaHome, installedJdk);
                            }
                        });
                    }
                } else {
                    switch (probeResult) {
                        case NO_SUCH_DIRECTORY:
                            throw new InvalidModelException(String.format("Path to JDK '%s' doesn't exist: %s", jdk.getName(), javaHome));
                        case INVALID_JDK:
                            throw new InvalidModelException(String.format("JDK '%s' is not a valid JDK installation: %s", jdk.getName(), javaHome));
                    }
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
                    jar.setDescription(String.format("Creates the binary file for %s.", binary));
                    jar.from(assembly.getClassDirectories());
                    jar.from(assembly.getResourceDirectories());
                    jar.setDestinationDir(runtimeJarDestDir);
                    jar.setArchiveName(runtimeJarArchiveName);
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
                    apiJarTask.setDescription(String.format("Creates the API binary file for %s.", binary));
                    apiJarTask.setOutputFile(apiJarFile.getFile());
                    apiJarTask.setExportedPackages(exportedPackages);
                    configureApiJarInputs(apiJarTask, assembly);
                    apiJarTask.dependsOn(assembly);
                    apiJarFile.setBuildTask(apiJarTask);
                }
            });
        }

        private void configureApiJarInputs(ApiJar apiJarTask, JvmAssembly assembly) {
            for (File classDir : assembly.getClassDirectories()) {
                apiJarTask.getInputs().sourceDir(classDir);
            }
        }

        private String apiJarTaskName(JarBinarySpecInternal binary) {
            String binaryName = binary.getProjectScopedName();
            String libName = binaryName.endsWith("Jar")
                ? binaryName.substring(0, binaryName.length() - 3)
                : binaryName;
            return libName + "ApiJar";
        }

        private static void checkDuplicateForPath(ImmutableListMultimap<String, JdkSpec> index, String path, List<String> errors) {
            ImmutableList<JdkSpec> jdkSpecs = index.get(path);
            if (jdkSpecs.size() > 1) {
                errors.add(String.format("   - %s are both pointing to the same JDK installation path: %s",
                    Joiner.on(", ").join(Iterables.transform(jdkSpecs, new Function<JdkSpec, String>() {
                        @Override
                        public String apply(JdkSpec input) {
                            return input.getName();
                        }
                    })), path));
            }
        }

        private static ImmutableListMultimap<String, JdkSpec> indexByPath(ModelMap<JdkSpec> jdks) {
            return Multimaps.index(toImmutableJdkList(jdks), new Function<JdkSpec, String>() {
                @Override
                public String apply(JdkSpec input) {
                    try {
                        return input.getPath().getCanonicalPath().toString();
                    } catch (IOException e) {
                        UncheckedException.throwAsUncheckedException(e);
                    }
                    return null;
                }
            });
        }

        private static List<JdkSpec> toImmutableJdkList(ModelMap<JdkSpec> jdks) {
            final List<JdkSpec> asImmutable = Lists.newArrayList();
            jdks.afterEach(new Action<JdkSpec>() {
                @Override
                public void execute(JdkSpec jdkSpec) {
                    asImmutable.add(jdkSpec);
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
            jarBinary.setJarFile(new File(jarsDir, String.format("%s.jar", libraryName)));
            jarBinary.setApiJarFile(new File(jarsDir, String.format("api/%s.jar", libraryName)));
            jarBinary.setToolChain(toolChains.getForPlatform(jarBinary.getTargetPlatform()));
        }
    }
}
