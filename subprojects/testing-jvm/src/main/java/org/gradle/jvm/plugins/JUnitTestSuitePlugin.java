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

import com.beust.jcommander.internal.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.*;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestTaskReports;
import org.gradle.internal.Transformers;
import org.gradle.internal.component.local.model.UsageKind;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.JarBinarySpec;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.JvmComponentSpec;
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.internal.*;
import org.gradle.jvm.test.JUnitTestSuiteBinarySpec;
import org.gradle.jvm.test.JUnitTestSuiteSpec;
import org.gradle.jvm.test.internal.DefaultJUnitTestSuiteBinarySpec;
import org.gradle.jvm.test.internal.DefaultJUnitTestSuiteSpec;
import org.gradle.jvm.test.internal.JUnitTestSuiteRules;
import org.gradle.jvm.test.internal.JvmTestSuites;
import org.gradle.jvm.toolchain.JavaToolChainRegistry;
import org.gradle.language.base.internal.model.DefaultVariantsMetaData;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.language.base.internal.resolve.LocalComponentResolveContext;
import org.gradle.language.java.JavaSourceSet;
import org.gradle.language.java.plugins.JavaLanguagePlugin;
import org.gradle.language.java.tasks.PlatformJavaCompile;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.PlatformResolvers;
import org.gradle.testing.base.plugins.TestingModelBasePlugin;
import org.gradle.util.CollectionUtils;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.Set;

import static org.gradle.jvm.internal.DefaultJvmBinarySpec.collectDependencies;
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
    static class PluginRules extends RuleSource {

        @Mutate
        public void registerClasspathConfigurer(LanguageTransformContainer languages, final ServiceRegistry serviceRegistry, final ModelSchemaStore schemaStore) {
            languages.withType(JavaLanguagePlugin.Java.class).all(new Action<JavaLanguagePlugin.Java>() {
                @Override
                public void execute(JavaLanguagePlugin.Java java) {
                    java.registerPlatformJavaCompileConfig(new JavaLanguagePlugin.Java.PlatformJavaCompileConfig() {
                        @Override
                        public void configureJavaCompile(BinarySpec spec, JavaSourceSet sourceSet, PlatformJavaCompile javaCompile) {
                            if (spec instanceof JUnitTestSuiteBinarySpec) {
                                JUnitTestSuiteBinarySpec testSuiteBinary = (JUnitTestSuiteBinarySpec) spec;
                                JvmBinarySpec testedBinary = testSuiteBinary.getTestedBinary();
                                if (testedBinary instanceof JarBinarySpecInternal) {
                                    FileCollection classpath = javaCompile.getClasspath();
                                    JvmAssembly assembly = ((WithJvmAssembly) testedBinary).getAssembly();
                                    ArtifactDependencyResolver dependencyResolver = serviceRegistry.get(ArtifactDependencyResolver.class);
                                    RepositoryHandler repositories = serviceRegistry.get(RepositoryHandler.class);
                                    List<ResolutionAwareRepository> resolutionAwareRepositories = CollectionUtils.collect(repositories, Transformers.cast(ResolutionAwareRepository.class));
                                    JvmComponentSpec testedComponent = JvmTestSuites.getTestedComponent(serviceRegistry, testSuiteBinary.getTestSuite().getTestedComponent());
                                    JvmLibrarySpec jvmLibrarySpec = (JvmLibrarySpec) testedComponent;
                                    BaseDependencyResolvingClasspath transitiveCompileClasspath = new BaseDependencyResolvingClasspath(
                                        (BinarySpecInternal) testedBinary,
                                        "test suite",
                                        dependencyResolver,
                                        resolutionAwareRepositories,
                                        new LocalComponentResolveContext(((BinarySpecInternal) testedBinary).getId(),
                                            DefaultVariantsMetaData.extractFrom(testedBinary, schemaStore),
                                            collectDependencies(testedBinary, testedComponent, ((JarBinarySpecInternal)testedBinary).getApiDependencies()),
                                            UsageKind.RUNTIME,
                                            testedBinary.getDisplayName()
                                        ));
                                    FileCollection fullClasspath = new UnionFileCollection(
                                        classpath,
                                        transitiveCompileClasspath,
                                        new SimpleFileCollection(assembly.getClassDirectories()),
                                        new SimpleFileCollection(assembly.getResourceDirectories()));
                                    javaCompile.setClasspath(fullClasspath);
                                    javaCompile.dependsOn(((WithJvmAssembly) testedBinary).getAssembly());
                                }
                            }
                        }
                    });
                }
            });
        }

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
                                 final @Path("buildDir") File buildDir,
                                 final ServiceRegistry registry,
                                 final ModelSchemaStore schemaStore) {

            final JvmAssembly jvmAssembly = ((WithJvmAssembly) binary).getAssembly();
            tasks.create(testTaskNameFor(binary), Test.class, new Action<Test>() {
                @Override
                public void execute(final Test test) {
                    test.dependsOn(jvmAssembly);
                    test.setTestClassesDir(binary.getClassesDir());
                    String testedComponentName = binary.getTestSuite().getTestedComponent();
                    JvmComponentSpec testedComponent = testedComponentName != null ? JvmTestSuites.getTestedComponent(registry, testedComponentName) : null;
                    test.setClasspath(classpathFor(binary, testedComponent, registry, schemaStore));
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
        void createJUnitComponentBinaries(ModelMap<BinarySpec> testBinaries,
                                          ServiceRegistry registry,
                                          PlatformResolvers platformResolver,
                                          JUnitTestSuiteSpec testSuite,
                                          JavaToolChainRegistry toolChains) {
            final String jUnitVersion = testSuite.getJUnitVersion();
            final DependencySpecContainer dependencies = testSuite.getDependencies();
            addJUnitDependencyTo(dependencies, jUnitVersion);
            JvmTestSuites.createJvmTestSuiteBinaries(testBinaries, registry, testSuite, JUnitTestSuiteBinarySpec.class, toolChains, platformResolver, new Action<JUnitTestSuiteBinarySpec>() {

                @Override
                public void execute(JUnitTestSuiteBinarySpec jUnitTestSuiteBinarySpec) {
                    jUnitTestSuiteBinarySpec.setJUnitVersion(jUnitVersion);
                    setDependenciesOf(jUnitTestSuiteBinarySpec, dependencies);
                }
            });
        }

        private void setDependenciesOf(JUnitTestSuiteBinarySpec binary, DependencySpecContainer dependencies) {
            ((WithDependencies) binary).setDependencies(Lists.newArrayList(dependencies.getDependencies()));
        }

        private void addJUnitDependencyTo(DependencySpecContainer dependencies, String jUnitVersion) {
            dependencies.group("junit").module("junit").version(jUnitVersion);
        }

        private JUnitDependencyResolvingClasspath classpathFor(JUnitTestSuiteBinarySpec test,
                                                               JvmComponentSpec testedComponent,
                                                               ServiceRegistry serviceRegistry,
                                                               ModelSchemaStore schemaStore) {
            ArtifactDependencyResolver dependencyResolver = serviceRegistry.get(ArtifactDependencyResolver.class);
            RepositoryHandler repositories = serviceRegistry.get(RepositoryHandler.class);
            List<ResolutionAwareRepository> resolutionAwareRepositories = CollectionUtils.collect(repositories, Transformers.cast(ResolutionAwareRepository.class));
            return new JUnitDependencyResolvingClasspath(test, testedComponent, "test suite", dependencyResolver, resolutionAwareRepositories, schemaStore);
        }
    }

    private static String testTaskNameFor(JUnitTestSuiteBinarySpec binary) {
        return ((BinarySpecInternal) binary).getProjectScopedName() + "Test";
    }

    private static class JUnitDependencyResolvingClasspath extends BaseDependencyResolvingClasspath {

        private final JvmBinarySpec testedBinary;
        private final JvmAssembly assembly;

        @SuppressWarnings("unchecked")
        protected JUnitDependencyResolvingClasspath(
            JUnitTestSuiteBinarySpec testSuiteBinarySpec,
            JvmComponentSpec testedComponent,
            String descriptor,
            ArtifactDependencyResolver dependencyResolver,
            List<ResolutionAwareRepository> remoteRepositories,
            ModelSchemaStore schemaStore) {
            super((BinarySpecInternal) testSuiteBinarySpec, descriptor, dependencyResolver, remoteRepositories, new LocalComponentResolveContext(
                ((BinarySpecInternal) testSuiteBinarySpec).getId(),
                DefaultVariantsMetaData.extractFrom(testSuiteBinarySpec, schemaStore),
                collectAllDependencies(testSuiteBinarySpec, testedComponent, testSuiteBinarySpec.getTestedBinary()),
                UsageKind.RUNTIME,
                testSuiteBinarySpec.getDisplayName()
            ));
            this.testedBinary = testSuiteBinarySpec.getTestedBinary();
            this.assembly = ((WithJvmAssembly) testSuiteBinarySpec).getAssembly();
        }

        @Override
        public TaskDependency getBuildDependencies() {
            if (testedBinary != null) {
                DefaultTaskDependency dependencies = new DefaultTaskDependency();
                dependencies.add(super.getBuildDependencies());
                dependencies.add(testedBinary);
                return dependencies;
            }

            return super.getBuildDependencies();
        }

        @SuppressWarnings("unchecked")
        private static List<DependencySpec> collectAllDependencies(JUnitTestSuiteBinarySpec testSuiteBinarySpec, JvmComponentSpec testedComponent, JvmBinarySpec testedBinary) {
            JUnitTestSuiteSpec testSuite = testSuiteBinarySpec.getTestSuite();
            List<DependencySpec> deps = collectDependencies(testSuiteBinarySpec, testSuite, testSuite.getDependencies().getDependencies());
            if (testedBinary instanceof JarBinarySpec && testedComponent instanceof JvmLibrarySpec) {
                List<DependencySpec> testedBinaryDeps = collectDependencies(testedBinary, testedComponent, ((JarBinarySpecInternal)testedBinary).getApiDependencies(), ((JvmLibrarySpec) testedComponent).getDependencies().getDependencies());
                deps.addAll(testedBinaryDeps);
            }
            return deps;
        }

        @Override
        public Set<File> getFiles() {
            // classpath order should not matter, but in practice, it is often the case that you can have multiple
            // instances of the same class on classpath. So we will use a linked hash set and add the elements in that
            // order:
            // 1. classes of the test suite
            // 2. classes of the tested component
            // 3. classes of dependencies of the test suite and dependencies of the tested component
            Set<File> classpath = Sets.newLinkedHashSet();
            addAssemblyToClasspath(classpath, assembly);
            addTestedComponentAssemblyToClasspath(classpath);
            addTestDependenciesToClassPath(classpath, super.getFiles());
            return classpath;
        }

        private void addTestDependenciesToClassPath(Set<File> classpath, Set<File> dependencies) {
            classpath.addAll(dependencies);
        }

        private void addTestedComponentAssemblyToClasspath(Set<File> classpath) {
            if (testedBinary instanceof WithJvmAssembly) {
                JvmAssembly testedComponentAssembly = ((WithJvmAssembly) testedBinary).getAssembly();
                addAssemblyToClasspath(classpath, testedComponentAssembly);
            }
        }

        private void addAssemblyToClasspath(Set<File> classpath, JvmAssembly assembly) {
            classpath.addAll(assembly.getClassDirectories());
            classpath.addAll(assembly.getResourceDirectories());
        }
    }
}
