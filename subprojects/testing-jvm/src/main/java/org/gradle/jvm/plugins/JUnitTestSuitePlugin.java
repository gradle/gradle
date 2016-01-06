/*
 * Copyright 2015 the original author or authors.
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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.*;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestTaskReports;
import org.gradle.internal.Transformers;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.internal.AbstractDependencyResolvingClasspath;
import org.gradle.jvm.internal.JvmAssembly;
import org.gradle.jvm.internal.WithDependencies;
import org.gradle.jvm.internal.WithJvmAssembly;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.test.JUnitTestSuiteBinarySpec;
import org.gradle.jvm.test.JUnitTestSuiteSpec;
import org.gradle.jvm.test.internal.DefaultJUnitTestSuiteBinarySpec;
import org.gradle.jvm.test.internal.DefaultJUnitTestSuiteSpec;
import org.gradle.jvm.test.internal.JUnitTestSuiteRules;
import org.gradle.jvm.toolchain.JavaToolChainRegistry;
import org.gradle.language.base.DependentSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetaData;
import org.gradle.language.base.internal.model.DefaultVariantsMetaData;
import org.gradle.language.base.internal.model.VariantsMetaData;
import org.gradle.language.base.internal.resolve.LocalLibraryResolveContext;
import org.gradle.model.ModelMap;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.*;
import org.gradle.testing.base.plugins.TestingModelBasePlugin;
import org.gradle.util.CollectionUtils;

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.gradle.model.internal.core.ModelNodes.withType;
import static org.gradle.model.internal.core.NodePredicate.allDescendants;

/**
 * This plugin adds support for execution of JUnit test suites to the Java software model.
 *
 * @since 2.11
 */
@Incubating
public class JUnitTestSuitePlugin implements Plugin<Project> {

    private final ModelRegistry modelRegistry;

    @Inject
    public JUnitTestSuitePlugin(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(TestingModelBasePlugin.class);
        project.getPluginManager().apply(JvmComponentPlugin.class);
        applyJUnitTestSuiteRules();
    }

    private void applyJUnitTestSuiteRules() {
        modelRegistry.getRoot().applyTo(allDescendants(withType(JUnitTestSuiteSpec.class)), JUnitTestSuiteRules.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {

        @ComponentType
        public void register(ComponentTypeBuilder<JUnitTestSuiteSpec> builder) {
            builder.defaultImplementation(DefaultJUnitTestSuiteSpec.class);
        }

        @BinaryType
        public void registerJUnitBinary(BinaryTypeBuilder<JUnitTestSuiteBinarySpec> builder) {
            builder.defaultImplementation(DefaultJUnitTestSuiteBinarySpec.class);
        }

        @BinaryTasks
        void createTestSuiteTask(final ModelMap<Task> tasks,
                                 final JUnitTestSuiteBinarySpec binary,
                                 final FileOperations fileOperations,
                                 final @Path("buildDir") File buildDir,
                                 final ServiceRegistry registry,
                                 final ModelSchemaStore schemaStore) {

            final JvmAssembly jvmAssembly = ((WithJvmAssembly) binary).getAssembly();
            tasks.create(testTaskNameFor(binary), Test.class, new Action<Test>() {
                @Override
                public void execute(final Test test) {
                    test.dependsOn(jvmAssembly);
                    test.setTestClassesDir(binary.getClassesDir());
                    test.setClasspath(classpathFor(binary, registry, schemaStore));
                    configureReports(test);
                }

                private void configureReports(Test test) {
                    // todo: improve configuration of reports
                    TestTaskReports reports = test.getReports();
                    File reportsDirectory = new File(buildDir, "reports");
                    File htmlDir = new File(reportsDirectory, "tests");
                    File xmlDir = new File(buildDir, "test-results");
                    File binDir = new File(xmlDir, "binary");
                    reports.getHtml().setDestination(htmlDir);
                    reports.getJunitXml().setDestination(xmlDir);
                    test.setBinResultsDir(binDir);
                }
            });
        }

        /**
         * Create binaries for test suites.
         */
        @ComponentBinaries
        void createJUnitComponentBinaries(ModelMap<BinarySpec> testBinaries, PlatformResolvers platformResolver, final JUnitTestSuiteSpec testSuite, final JavaToolChainRegistry toolChains) {
            final List<JavaPlatform> javaPlatforms = resolvePlatforms(platformResolver);
            final JavaPlatform platform = javaPlatforms.get(0);
            final BinaryNamingScheme namingScheme = namingSchemeFor(testSuite, javaPlatforms, platform);
            testBinaries.create(namingScheme.getBinaryName(), JUnitTestSuiteBinarySpec.class, new Action<JUnitTestSuiteBinarySpec>() {

                @Override
                public void execute(JUnitTestSuiteBinarySpec jUnitTestSuiteBinarySpec) {
                    final String jUnitVersion = testSuite.getJUnitVersion();
                    ((BinarySpecInternal) jUnitTestSuiteBinarySpec).setNamingScheme(namingScheme);
                    jUnitTestSuiteBinarySpec.setJUnitVersion(jUnitVersion);
                    jUnitTestSuiteBinarySpec.setTargetPlatform(platform);
                    jUnitTestSuiteBinarySpec.setToolChain(toolChains.getForPlatform(platform));

                    DependencySpecContainer dependencies = testSuite.getDependencies();
                    addJUnitDependencyTo(dependencies, jUnitVersion);
                    setDependenciesOf(jUnitTestSuiteBinarySpec, dependencies);
                }
            });
        }

        private void setDependenciesOf(JUnitTestSuiteBinarySpec binary, DependencySpecContainer dependencies) {
            ((WithDependencies) binary).setDependencies(dependencies.getDependencies());
        }

        private void addJUnitDependencyTo(DependencySpecContainer dependencies, String jUnitVersion) {
            dependencies.group("junit").module("junit").version(jUnitVersion);
        }

        private static List<JavaPlatform> resolvePlatforms(final PlatformResolvers platformResolver) {
            PlatformRequirement defaultPlatformRequirement = DefaultPlatformRequirement.create(DefaultJavaPlatform.current().getName());
            return Collections.singletonList(platformResolver.resolve(JavaPlatform.class, defaultPlatformRequirement));
        }

        private BinaryNamingScheme namingSchemeFor(JUnitTestSuiteSpec testSuiteSpec, List<JavaPlatform> selectedPlatforms, JavaPlatform platform) {
            return DefaultBinaryNamingScheme.component(testSuiteSpec.getName())
                .withBinaryType("binary") // not a 'Jar', not a 'test'
                .withRole("assembly", true)
                .withVariantDimension(platform, selectedPlatforms);
        }

        private JUnitDependencyResolvingClasspath classpathFor(final JUnitTestSuiteBinarySpec test, final ServiceRegistry serviceRegistry, final ModelSchemaStore schemaStore) {
            ArtifactDependencyResolver dependencyResolver = serviceRegistry.get(ArtifactDependencyResolver.class);
            RepositoryHandler repositories = serviceRegistry.get(RepositoryHandler.class);
            List<ResolutionAwareRepository> resolutionAwareRepositories = CollectionUtils.collect(repositories, Transformers.cast(ResolutionAwareRepository.class));
            return new JUnitDependencyResolvingClasspath(test, "test suite", dependencyResolver, resolutionAwareRepositories, new LocalLibraryResolveContext() {

                @Override
                public VariantsMetaData getVariants() {
                    return DefaultVariantsMetaData.extractFrom(test, schemaStore);
                }

                private final ResolutionStrategyInternal resolutionStrategy = new DefaultResolutionStrategy();

                @Override
                public String getName() {
                    return "API";
                }

                @Override
                public String getDisplayName() {
                    return test.getDisplayName();
                }

                @Override
                public ResolutionStrategyInternal getResolutionStrategy() {
                    return resolutionStrategy;
                }

                @Override
                public ComponentResolveMetaData toRootComponentMetaData() {
                    LibraryBinaryIdentifier binaryIdentifier = ((BinarySpecInternal) test).getId();
                    DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
                    return DefaultLibraryLocalComponentMetaData.newDefaultLibraryLocalComponentMetadata(
                        binaryIdentifier,
                        buildDependencies,
                        collectDependencies(test),
                        binaryIdentifier.getProjectPath());
                }

                private List<DependencySpec> collectDependencies(JUnitTestSuiteBinarySpec binary) {
                    final List<DependencySpec> dependencies = Lists.newArrayList();
                    Iterable<LanguageSourceSet> sourceSets = Iterables.concat(binary.getTestSuite().getSources().values(), binary.getSources().values());
                    for (LanguageSourceSet sourceSet : sourceSets) {
                        if (sourceSet instanceof DependentSourceSet) {
                            dependencies.addAll(((DependentSourceSet) sourceSet).getDependencies().getDependencies());
                        }
                    }
                    dependencies.addAll(((WithDependencies) binary).getDependencies());
                    return dependencies;
                }
            });
        }
    }

    private static String testTaskNameFor(JUnitTestSuiteBinarySpec binary) {
        return ((BinarySpecInternal) binary).getProjectScopedName() + "Test";
    }

    private static class JUnitDependencyResolvingClasspath extends AbstractDependencyResolvingClasspath {

        private final JvmAssembly assembly;

        protected JUnitDependencyResolvingClasspath(JUnitTestSuiteBinarySpec binarySpec, String descriptor, ArtifactDependencyResolver dependencyResolver, List<ResolutionAwareRepository> remoteRepositories, ResolveContext resolveContext) {
            super((BinarySpecInternal) binarySpec, descriptor, dependencyResolver, remoteRepositories, resolveContext);
            this.assembly = ((WithJvmAssembly) binarySpec).getAssembly();
        }

        @Override
        public Set<File> getFiles() {
            return Sets.union(super.getFiles(), Sets.union(assembly.getClassDirectories(), assembly.getResourceDirectories()));
        }
    }
}
