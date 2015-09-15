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

import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Plugin;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.rules.ModelMapCreators;
import org.gradle.api.internal.rules.NamedDomainObjectFactoryRegistry;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.util.BiFunction;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.model.BinarySpecFactoryRegistry;
import org.gradle.language.base.internal.model.ComponentBinaryRules;
import org.gradle.language.base.internal.model.ComponentRules;
import org.gradle.language.base.internal.registry.*;
import org.gradle.model.*;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.SpecializedMapSchema;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.*;
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
    private final ModelSchemaStore schemaStore;
    private final NodeInitializerRegistry nodeInitializerRegistry;

    @Inject
    public ComponentModelBasePlugin(ModelRegistry modelRegistry, ModelSchemaStore schemaStore, NodeInitializerRegistry nodeInitializerRegistry) {
        this.modelRegistry = modelRegistry;
        this.schemaStore = schemaStore;
        this.nodeInitializerRegistry = nodeInitializerRegistry;
    }

    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(LanguageBasePlugin.class);

        SimpleModelRuleDescriptor descriptor = new SimpleModelRuleDescriptor(ComponentModelBasePlugin.class.getSimpleName() + ".apply()");

        SpecializedMapSchema<ComponentSpecContainer> schema = (SpecializedMapSchema<ComponentSpecContainer>) schemaStore.getSchema(ModelType.of(ComponentSpecContainer.class));
        ModelPath components = ModelPath.path("components");
        ModelCreator componentsCreator = ModelMapCreators.specialized(
            components,
            ComponentSpec.class,
            ComponentSpecContainer.class,
            schema.getImplementationType().asSubclass(ComponentSpecContainer.class),
            nodeInitializerRegistry,
            descriptor
        );
        modelRegistry.create(componentsCreator);
        modelRegistry.getRoot().applyToAllLinksTransitive(ModelType.of(ComponentSpec.class), ComponentRules.class);
        modelRegistry.getRoot().applyToAllLinksTransitive(ModelType.of(ComponentSpec.class), ComponentBinaryRules.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @Model
        ComponentSpecFactory componentSpecFactory() {
            return new ComponentSpecFactory("this collection");
        }

        @Model
        LanguageRegistry languages(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(Instantiator.class).newInstance(DefaultLanguageRegistry.class);
        }

        @Model
        LanguageTransformContainer languageTransforms(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(Instantiator.class).newInstance(DefaultLanguageTransformContainer.class);
        }

        // Required because creation of Binaries from Components is not yet wired into the infrastructure
        @Mutate
        void closeComponentsForBinaries(ModelMap<Task> tasks, ComponentSpecContainer components) {
        }

        // Finalizing here, as we need this to run after any 'assembling' task (jar, link, etc) is created.
        @Finalize
        void createSourceTransformTasks(final TaskContainer tasks, final BinaryContainer binaries, LanguageTransformContainer languageTransforms, ServiceRegistry serviceRegistry) {
            for (LanguageTransform<?, ?> language : languageTransforms) {
                for (final BinarySpecInternal binary : binaries.withType(BinarySpecInternal.class)) {
                    if (binary.isLegacyBinary() || !language.applyToBinary(binary)) {
                        continue;
                    }

                    final SourceTransformTaskConfig taskConfig = language.getTransformTask();
                    for (LanguageSourceSet languageSourceSet : binary.getInputs()) {
                        LanguageSourceSetInternal sourceSet = (LanguageSourceSetInternal) languageSourceSet;
                        if (language.getSourceSetType().isInstance(sourceSet) && sourceSet.getMayHaveSources()) {
                            String taskName = taskConfig.getTaskPrefix() + capitalize(binary.getName()) + capitalize(sourceSet.getFullName());
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

        @Model
        PlatformResolvers platformResolver(PlatformContainer platforms, ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            return instantiator.newInstance(DefaultPlatformResolvers.class, platforms);
        }

        @Mutate
        void registerPlatformExtension(ExtensionContainer extensions, PlatformContainer platforms) {
            extensions.add("platforms", platforms);
        }

        @Model
        BinarySpecFactory binarySpecFactory(final BinarySpecFactoryRegistry binaryFactoryRegistry) {
            // BinarySpecFactoryRegistry is used by the BinaryContainer API, which we still need for the time being.
            // We are adapting it to BinarySpecFactory here so it can be used by component.binaries model maps

            final BinarySpecFactory binarySpecFactory = new BinarySpecFactory("this collection");
            binaryFactoryRegistry.copyInto(new NamedDomainObjectFactoryRegistry<BinarySpec>() {
                @Override
                public <U extends BinarySpec> void registerFactory(Class<U> type, final NamedDomainObjectFactory<? extends U> factory) {
                    binarySpecFactory.register(type, null, new BiFunction<U, String, MutableModelNode>() {
                        @Override
                        public U apply(String s, MutableModelNode modelNode) {
                            final U binarySpec = factory.create(s);
                            final Object parentObject = modelNode.getParent().getParent().getPrivateData();
                            if (parentObject instanceof ComponentSpec && binarySpec instanceof ComponentSpecAware) {
                                ((ComponentSpecAware) binarySpec).setComponent((ComponentSpec) parentObject);
                            }

                            return binarySpec;
                        }
                    });
                }
            });
            return binarySpecFactory;
        }

        @Model
        InstanceFactoryRegistry createInstanceFactoryRegistry(ServiceRegistry serviceRegistry, BinarySpecFactory binarySpecFactory, ComponentSpecFactory componentSpecFactory) {
            InstanceFactoryRegistry instanceFactoryRegistry = serviceRegistry.get(InstanceFactoryRegistry.class);
            for (Class<? extends BinarySpec> type : binarySpecFactory.getSupportedTypes()) {
                instanceFactoryRegistry.register(ModelType.of(type), ModelReference.of(BinarySpecFactory.class));
            }
            for (Class<? extends ComponentSpec> type : componentSpecFactory.getSupportedTypes()) {
                instanceFactoryRegistry.register(ModelType.of(type), ModelReference.of(ComponentSpecFactory.class));
            }
            return instanceFactoryRegistry;
        }

        @Model
        NodeInitializerRegistry createNodeInitializerRegistry(ServiceRegistry serviceRegistry, InstanceFactoryRegistry instanceFactoryRegistry) {
            return serviceRegistry.get(NodeInitializerRegistry.class);
        }

        @Defaults
        void collectBinaries(BinaryContainer binaries, ComponentSpecContainer componentSpecs) {
            for (ComponentSpec componentSpec : componentSpecs.values()) {
                for (BinarySpec binary : componentSpec.getBinaries().values()) {
                    binaries.add(binary);
                }
            }
        }

        // TODO:LPTR This should be done on the binary itself when transitive rules don't fire multiple times anymore
        @Finalize
        void addSourceSetsOwnedByBinariesToTheirInputs(BinaryContainer binarySpecs) {
            for (BinarySpec binary : binarySpecs) {
                if (((BinarySpecInternal) binary).isLegacyBinary()) {
                    continue;
                }
                binary.getInputs().addAll(binary.getSources().values());
            }
        }
    }
}
