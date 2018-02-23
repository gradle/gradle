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

package org.gradle.nativeplatform.test.plugins;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.nativeplatform.DependentSourceSet;
import org.gradle.model.Defaults;
import org.gradle.model.Each;
import org.gradle.model.Finalize;
import org.gradle.model.ModelMap;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelTypes;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.internal.NativeDependentBinariesResolutionStrategy;
import org.gradle.nativeplatform.plugins.NativeComponentPlugin;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.internal.DefaultNativeTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.internal.NativeDependentBinariesResolutionStrategyTestSupport;
import org.gradle.nativeplatform.test.internal.NativeTestSuiteBinarySpecInternal;
import org.gradle.nativeplatform.test.tasks.RunTestExecutable;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.dependents.DependentBinariesResolvedResult;
import org.gradle.platform.base.internal.dependents.DependentBinariesResolver;
import org.gradle.testing.base.TestSuiteContainer;
import org.gradle.testing.base.plugins.TestingModelBasePlugin;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * A plugin that sets up the infrastructure for testing native binaries.
 */
@Incubating
public class NativeBinariesTestPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(NativeComponentPlugin.class);
        project.getPluginManager().apply(TestingModelBasePlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @ComponentType
        void nativeTestSuiteBinary(TypeBuilder<NativeTestSuiteBinarySpec> builder) {
            builder.defaultImplementation(DefaultNativeTestSuiteBinarySpec.class);
            builder.internalView(NativeTestSuiteBinarySpecInternal.class);
        }

        @Finalize
        void attachTestedBinarySourcesToTestBinaries(@Each final NativeTestSuiteBinarySpecInternal testSuiteBinary) {
            final BinarySpec testedBinary = testSuiteBinary.getTestedBinary();
            testSuiteBinary.getInputs().withType(DependentSourceSet.class).all(new Action<DependentSourceSet>() {
                @Override
                public void execute(DependentSourceSet testSource) {
                    testSource.lib(testedBinary.getInputs());
                }
            });
            testedBinary.getInputs().all(new Action<LanguageSourceSet>() {
                @Override
                public void execute(LanguageSourceSet testedSource) {
                    testSuiteBinary.getInputs().add(testedSource);
                }
            });
        }

        @Finalize
        void configureRunTask(@Each NativeTestSuiteBinarySpecInternal testSuiteBinary) {
            BinaryNamingScheme namingScheme = testSuiteBinary.getNamingScheme();
            NativeTestSuiteBinarySpec.TasksCollection tasks = testSuiteBinary.getTasks();
            InstallExecutable installTask = (InstallExecutable) tasks.getInstall();
            RunTestExecutable runTask = (RunTestExecutable) tasks.getRun();
            runTask.getInputs().files(installTask.getOutputs().getFiles()).withPropertyName("installTask.outputs");
            runTask.setExecutable(installTask.getRunScript().getPath());
            Project project = runTask.getProject();
            runTask.setOutputDir(namingScheme.getOutputDirectory(project.getBuildDir(), "test-results"));
        }

        @Defaults
        void registerNativeDependentBinariesResolutionStrategyTestSupport(DependentBinariesResolver resolver) {
            NativeDependentBinariesResolutionStrategy nativeStrategy = resolver.getStrategy(NativeDependentBinariesResolutionStrategy.NAME, NativeDependentBinariesResolutionStrategy.class);
            nativeStrategy.setTestSupport(new NativeDependentBinariesResolutionStrategyTestSupport());
        }

        @Finalize
        public void wireBuildDependentsTasks(ModelMap<Task> tasks, TestSuiteContainer testSuites, final BinaryContainer binaries, final DependentBinariesResolver dependentsResolver, ServiceRegistry serviceRegistry) {
            final ProjectModelResolver projectModelResolver = serviceRegistry.get(ProjectModelResolver.class);
            final ModelMap<NativeBinarySpecInternal> nativeBinaries = binaries.withType(NativeBinarySpecInternal.class);
            for (final NativeBinarySpecInternal binary : nativeBinaries) {
                Task buildDependents = tasks.get(binary.getNamingScheme().getTaskName("buildDependents"));
                Callable<Iterable<Task>> deferredDependencies = new Callable<Iterable<Task>>() {
                    @Override
                    public Iterable<Task> call() {
                        List<Task> dependencies = Lists.newArrayList();
                        DependentBinariesResolvedResult result = dependentsResolver.resolve(binary).getRoot();
                        for (DependentBinariesResolvedResult dependent : result.getChildren()) {
                            if (dependent.isBuildable() && dependent.isTestSuite()) {
                                ModelRegistry modelRegistry = projectModelResolver.resolveProjectModel(dependent.getId().getProjectPath());
                                ModelMap<NativeBinarySpecInternal> projectBinaries = modelRegistry.realize("binaries", ModelTypes.modelMap(NativeBinarySpecInternal.class));
                                NativeBinarySpecInternal dependentBinary = projectBinaries.get(dependent.getProjectScopedName());
                                NativeTestSuiteBinarySpecInternal testSuiteBinary = (NativeTestSuiteBinarySpecInternal) dependentBinary;
                                dependencies.add(testSuiteBinary.getCheckTask());
                            }
                        }
                        return dependencies;
                    }
                };
                buildDependents.dependsOn(deferredDependencies);
            }
            for (NativeTestSuiteBinarySpecInternal testSuiteBinary : nativeBinaries.withType(NativeTestSuiteBinarySpecInternal.class)) {
                Task buildDependents = tasks.get(testSuiteBinary.getNamingScheme().getTaskName("buildDependents"));
                buildDependents.dependsOn(testSuiteBinary.getCheckTask());
            }
        }

    }
}
