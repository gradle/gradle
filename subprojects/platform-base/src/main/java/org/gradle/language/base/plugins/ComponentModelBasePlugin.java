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
package org.gradle.language.base.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.model.ComponentBinaryRules;
import org.gradle.language.base.internal.model.ComponentRules;
import org.gradle.language.base.internal.registry.DefaultLanguageTransformContainer;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.model.*;
import org.gradle.model.internal.core.NodeInitializerRegistry;
import org.gradle.model.internal.core.Service;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.extract.FactoryBasedNodeInitializerExtractionStrategy;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.*;
import org.gradle.platform.base.binary.internal.BinarySpecFactory;
import org.gradle.platform.base.component.BaseComponentSpec;
import org.gradle.platform.base.component.internal.ComponentSpecFactory;
import org.gradle.platform.base.internal.*;

import javax.inject.Inject;

import static org.apache.commons.lang.StringUtils.capitalize;

/**
 * Base plugin for language support.
 *
 * Adds a {@link org.gradle.platform.base.ComponentSpecContainer} named {@code componentSpecs} to the project.
 *
 * For each binary instance added to the binaries container, registers a lifecycle task to create that binary.
 */
@Incubating
public class ComponentModelBasePlugin implements Plugin<ProjectInternal> {
    private final ModelRegistry modelRegistry;

    @Inject
    public ComponentModelBasePlugin(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(LanguageBasePlugin.class);

        modelRegistry.getRoot().applyToAllLinksTransitive(ModelType.of(ComponentSpec.class), ComponentRules.class);
        modelRegistry.getRoot().applyToAllLinksTransitive(ModelType.of(ComponentSpec.class), ComponentBinaryRules.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        // TODO The 'path' is required to here to avoid a rule cycle being reported
        @Service
        ComponentSpecFactory componentSpecFactory(@Path("projectIdentifier") ProjectIdentifier projectIdentifier) {
            return new ComponentSpecFactory("components", projectIdentifier);
        }

        @ComponentType
        void registerBaseComponentSpec(ComponentTypeBuilder<ComponentSpec> builder) {
            builder.defaultImplementation(BaseComponentSpec.class);
            builder.internalView(ComponentSpecInternal.class);
        }

        @Service
        BinarySpecFactory binarySpecFactory(ServiceRegistry serviceRegistry, ITaskFactory taskFactory) {
            return new BinarySpecFactory("binaries", serviceRegistry.get(Instantiator.class), taskFactory);
        }

        @Model
        void components(ComponentSpecContainer componentSpecs) {}

        @Mutate
        void registerNodeInitializerExtractors(NodeInitializerRegistry nodeInitializerRegistry, ComponentSpecFactory componentSpecFactory, BinarySpecFactory binarySpecFactory, ModelSchemaStore schemaStore, ManagedProxyFactory proxyFactory) {
            nodeInitializerRegistry.registerStrategy(new FactoryBasedNodeInitializerExtractionStrategy<ComponentSpec>(componentSpecFactory));
            nodeInitializerRegistry.registerStrategy(new FactoryBasedNodeInitializerExtractionStrategy<BinarySpec>(binarySpecFactory));
        }

        @Service
        LanguageTransformContainer languageTransforms() {
            return new DefaultLanguageTransformContainer();
        }

        // Required because creation of Binaries from Components is not yet wired into the infrastructure
        @Mutate
        void closeComponentsForBinaries(ModelMap<Task> tasks, ComponentSpecContainer components) {
        }

        // Finalizing here, as we need this to run after any 'assembling' task (jar, link, etc) is created.
        @Finalize
        void createSourceTransformTasks(final TaskContainer tasks, final ModelMap<BinarySpecInternal> binaries, LanguageTransformContainer languageTransforms, ServiceRegistry serviceRegistry) {
            for (LanguageTransform<?, ?> language : languageTransforms) {
                for (final BinarySpecInternal binary : binaries) {
                    if (binary.isLegacyBinary() || !language.applyToBinary(binary)) {
                        continue;
                    }

                    final SourceTransformTaskConfig taskConfig = language.getTransformTask();
                    for (LanguageSourceSet languageSourceSet : binary.getInputs()) {
                        LanguageSourceSetInternal sourceSet = (LanguageSourceSetInternal) languageSourceSet;
                        if (language.getSourceSetType().isInstance(sourceSet) && sourceSet.getMayHaveSources()) {
                            String taskName = taskConfig.getTaskPrefix() + capitalize(binary.getProjectScopedName()) + capitalize(sourceSet.getProjectScopedName());
                            Task task = tasks.create(taskName, taskConfig.getTaskType());
                            taskConfig.configureTask(task, binary, sourceSet, serviceRegistry);

                            task.dependsOn(sourceSet);
                            binary.getTasks().add(task);
                        }
                    }
                }
            }
        }

        @Model
        PlatformContainer platforms(ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            return instantiator.newInstance(DefaultPlatformContainer.class, instantiator);
        }

        @Service
        PlatformResolvers platformResolver(PlatformContainer platforms) {
            return new DefaultPlatformResolvers(platforms);
        }

        @Mutate
        void registerPlatformExtension(ExtensionContainer extensions, PlatformContainer platforms) {
            extensions.add("platforms", platforms);
        }

        @Defaults
        void collectBinaries(ModelMap<BinarySpec> binaries, ComponentSpecContainer componentSpecs) {
            for (ComponentSpec componentSpec : componentSpecs.values()) {
                for (BinarySpec binary : componentSpec.getBinaries().values()) {
                    binaries.put(((BinarySpecInternal) binary).getProjectScopedName(), binary);
                }
            }
        }

        @Validate
        void validateComponentSpecRegistrations(ComponentSpecFactory instanceFactory) {
            instanceFactory.validateRegistrations();
        }

        @Validate
        void validateBinarySpecRegistrations(BinarySpecFactory instanceFactory) {
            instanceFactory.validateRegistrations();
        }

        // TODO:LPTR This should be done on the binary itself when transitive rules don't fire multiple times anymore
        @Defaults
        void addSourceSetsOwnedByBinariesToTheirInputs(ModelMap<BinarySpec> binarySpecs) {
            binarySpecs.withType(BinarySpecInternal.class).afterEach(new Action<BinarySpecInternal>() {
                @Override
                public void execute(BinarySpecInternal binary) {
                    if (binary.isLegacyBinary()) {
                        return;
                    }
                    binary.getInputs().addAll(binary.getSources().values());
                }
            });
        }
    }
}
