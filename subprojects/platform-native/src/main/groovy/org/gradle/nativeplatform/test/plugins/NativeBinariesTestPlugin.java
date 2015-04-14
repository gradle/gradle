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

import org.gradle.api.*;
import org.gradle.api.internal.DefaultDynamicTypesNamedEntityInstantiator;
import org.gradle.api.internal.rules.RuleAwareNamedDomainObjectFactoryRegistry;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.BiAction;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.nativeplatform.DependentSourceSet;
import org.gradle.model.Finalize;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.collection.internal.DynamicTypesCollectionBuilderProjection;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.plugins.NativeComponentPlugin;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.tasks.RunTestExecutable;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.rules.DefaultRuleAwareDynamicTypesNamedEntityInstantiator;
import org.gradle.platform.base.internal.rules.RuleAwareDynamicTypesNamedEntityInstantiator;
import org.gradle.platform.base.test.TestSuiteSpec;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

/**
 * A plugin that sets up the infrastructure for testing native binaries with CUnit.
 */
@Incubating
public class NativeBinariesTestPlugin implements Plugin<Project> {
    private final Instantiator instantiator;
    private final ModelRegistry modelRegistry;

    @Inject
    public NativeBinariesTestPlugin(Instantiator instantiator, ModelRegistry modelRegistry) {
        this.instantiator = instantiator;
        this.modelRegistry = modelRegistry;
    }

    public void apply(final Project project) {
        project.getPluginManager().apply(NativeComponentPlugin.class);

        String descriptor = NativeBinariesTestPlugin.class.getName() + ".apply()";

        ModelType<RuleAwareNamedDomainObjectFactoryRegistry<TestSuiteSpec>> factoryRegistryType = new ModelType<RuleAwareNamedDomainObjectFactoryRegistry<TestSuiteSpec>>() {
        };
        ModelReference<CollectionBuilder<TestSuiteSpec>> containerReference = ModelReference.of("testSuites", DefaultCollectionBuilder.typeOf(TestSuiteSpec.class));

        ModelCreator testSuitesCreator = ModelCreators.of(containerReference, new BiAction<MutableModelNode, List<ModelView<?>>>() {
            @Override
            public void execute(MutableModelNode mutableModelNode, List<ModelView<?>> modelViews) {
                final DefaultDynamicTypesNamedEntityInstantiator<TestSuiteSpec> namedEntityInstantiator = new DefaultDynamicTypesNamedEntityInstantiator<TestSuiteSpec>(
                        TestSuiteSpec.class, "this collection"
                );
                ModelType<RuleAwareDynamicTypesNamedEntityInstantiator<TestSuiteSpec>> instantiatorType = new ModelType<RuleAwareDynamicTypesNamedEntityInstantiator<TestSuiteSpec>>() {
                };
                mutableModelNode.setPrivateData(instantiatorType, new DefaultRuleAwareDynamicTypesNamedEntityInstantiator<TestSuiteSpec>(namedEntityInstantiator));
            }
        })
                .descriptor(descriptor)
                .ephemeral(true)
                .withProjection(new DynamicTypesCollectionBuilderProjection<TestSuiteSpec>(ModelType.of(TestSuiteSpec.class)))
                .withProjection(new UnmanagedModelProjection<RuleAwareNamedDomainObjectFactoryRegistry<TestSuiteSpec>>(factoryRegistryType))
                .build();
        modelRegistry.createOrReplace(testSuitesCreator);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @Finalize
            // Must run after test binaries have been created (currently in CUnit plugin)
        void attachTestedBinarySourcesToTestBinaries(BinaryContainer binaries) {
            for (NativeTestSuiteBinarySpec testSuiteBinary : binaries.withType(NativeTestSuiteBinarySpec.class)) {
                NativeBinarySpec testedBinary = testSuiteBinary.getTestedBinary();
                testSuiteBinary.source(testedBinary.getSource());

                for (DependentSourceSet testSource : testSuiteBinary.getSource().withType(DependentSourceSet.class)) {
                    testSource.lib(testedBinary.getSource());
                }
            }
        }

        @Finalize
        public void createTestTasks(final TaskContainer tasks, BinaryContainer binaries) {
            for (NativeTestSuiteBinarySpec testBinary : binaries.withType(NativeTestSuiteBinarySpec.class)) {
                NativeBinarySpecInternal binary = (NativeBinarySpecInternal) testBinary;
                final BinaryNamingScheme namingScheme = binary.getNamingScheme();

                RunTestExecutable runTask = tasks.create(namingScheme.getTaskName("run"), RunTestExecutable.class);
                final Project project = runTask.getProject();
                runTask.setDescription(String.format("Runs the %s", binary));

                final InstallExecutable installTask = binary.getTasks().withType(InstallExecutable.class).iterator().next();
                runTask.getInputs().files(installTask.getOutputs().getFiles());
                runTask.setExecutable(installTask.getRunScript().getPath());
                runTask.setOutputDir(new File(project.getBuildDir(), "/test-results/" + namingScheme.getOutputDirectoryBase()));

                testBinary.getTasks().add(runTask);
            }
        }

        @Mutate
        void attachBinariesToCheckLifecycle(CollectionBuilder<Task> tasks, final BinaryContainer binaries) {
            // TODO - binaries aren't an input to this rule, they're an input to the action
            tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, new Action<Task>() {
                @Override
                public void execute(Task checkTask) {
                    for (NativeTestSuiteBinarySpec testBinary : binaries.withType(NativeTestSuiteBinarySpec.class)) {
                        checkTask.dependsOn(testBinary.getTasks().getRun());
                    }
                }
            });
        }
    }
}
