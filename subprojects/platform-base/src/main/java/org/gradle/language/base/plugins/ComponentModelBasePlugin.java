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
import org.gradle.api.internal.DefaultPolymorphicNamedEntityInstantiator;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.rules.DefaultRuleAwarePolymorphicNamedEntityInstantiator;
import org.gradle.api.internal.rules.ModelMapCreators;
import org.gradle.api.internal.rules.RuleAwarePolymorphicNamedEntityInstantiator;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.model.BinarySpecFactoryRegistry;
import org.gradle.language.base.internal.model.ComponentRules;
import org.gradle.language.base.internal.model.ComponentSpecInitializer;
import org.gradle.language.base.internal.registry.*;
import org.gradle.model.*;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.manage.schema.ModelMapSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.*;

import javax.inject.Inject;

import static org.apache.commons.lang.StringUtils.capitalize;
import static org.gradle.internal.Cast.uncheckedCast;

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

    @Inject
    public ComponentModelBasePlugin(ModelRegistry modelRegistry, ModelSchemaStore schemaStore) {
        this.modelRegistry = modelRegistry;
        this.schemaStore = schemaStore;
    }

    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(LanguageBasePlugin.class);

        SimpleModelRuleDescriptor descriptor = new SimpleModelRuleDescriptor(ComponentModelBasePlugin.class.getName() + ".apply()");

        ModelReference<RuleAwarePolymorphicNamedEntityInstantiator<ComponentSpec>> componentTypeRegistryType = ModelReference.of(new ModelType<RuleAwarePolymorphicNamedEntityInstantiator<ComponentSpec>>() { });
        ModelMapSchema<ComponentSpecContainer> schema = (ModelMapSchema<ComponentSpecContainer>) schemaStore.getSchema(ModelType.of(ComponentSpecContainer.class));
        ModelPath components = ModelPath.path("components");
        ModelCreator componentsCreator = ModelMapCreators.specialized(components, ComponentSpec.class, ComponentSpecContainer.class, schema.getManagedImpl().asSubclass(ComponentSpecContainer.class), componentTypeRegistryType, descriptor);
        modelRegistry.create(componentsCreator);

        modelRegistry.getRoot().applyToAllLinksTransitive(ModelActionRole.Defaults,
            DirectNodeModelAction.of(
                ModelReference.of(ComponentSpec.class),
                descriptor,
                ComponentSpecInitializer.action()));
        modelRegistry.getRoot().applyToAllLinksTransitive(ModelType.of(ComponentSpec.class), ComponentRules.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @Model
        RuleAwarePolymorphicNamedEntityInstantiator<ComponentSpec> componentTypeRegistry() {
            return new DefaultRuleAwarePolymorphicNamedEntityInstantiator<ComponentSpec>(new DefaultPolymorphicNamedEntityInstantiator<ComponentSpec>(ComponentSpec.class, "this collection"));
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
        void createSourceTransformTasks(final TaskContainer tasks, final BinaryContainer binaries, LanguageTransformContainer languageTransforms) {
            for (LanguageTransform<?, ?> language : languageTransforms) {
                for (final BinarySpecInternal binary : binaries.withType(BinarySpecInternal.class)) {
                    if (binary.isLegacyBinary() || !language.applyToBinary(binary)) {
                        continue;
                    }

                    final SourceTransformTaskConfig taskConfig = language.getTransformTask();
                    binary.getSource().withType(language.getSourceSetType(), new Action<LanguageSourceSet>() {
                        public void execute(LanguageSourceSet languageSourceSet) {
                            LanguageSourceSetInternal sourceSet = (LanguageSourceSetInternal) languageSourceSet;
                            if (sourceSet.getMayHaveSources()) {
                                String taskName = taskConfig.getTaskPrefix() + capitalize(binary.getName()) + capitalize(sourceSet.getFullName());
                                Task task = tasks.create(taskName, taskConfig.getTaskType());

                                taskConfig.configureTask(task, binary, sourceSet);

                                task.dependsOn(sourceSet);
                                binary.getTasks().add(task);
                            }
                        }
                    });
                }
            }
        }

        @Mutate
        void applyDefaultSourceConventions(ComponentSpecContainer componentSpecs) {
            componentSpecs.afterEach(new Action<ComponentSpec>() {
                @Override
                public void execute(ComponentSpec componentSpec) {
                    for (LanguageSourceSet languageSourceSet : componentSpec.getSource().values()) {
                        // Only apply default locations when none explicitly configured
                        if (languageSourceSet.getSource().getSrcDirs().isEmpty()) {
                            languageSourceSet.getSource().srcDir(String.format("src/%s/%s", componentSpec.getName(), languageSourceSet.getName()));
                        }
                    }
                }
            });
        }

        // TODO:DAZ Work out why this is required
        @Mutate
        void closeSourcesForBinaries(BinaryContainer binaries, ProjectSourceSet sources) {
            // Only required because sources aren't fully integrated into model
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

        static class RegisterBinaryFactories extends RuleSource {
            @Defaults
            void apply(ComponentSpec componentSpec, BinarySpecFactoryRegistry binaryFactoryRegistry) {
                ComponentSpecInternal componentSpecInternal = uncheckedCast(componentSpec);
                binaryFactoryRegistry.copyInto(componentSpecInternal.getBinariesContainer());
            }
        }

        @Defaults
        void registerBinaryFactories(ComponentSpecContainer componentSpecs, final BinarySpecFactoryRegistry binaryFactoryRegistry) {
            componentSpecs.withType(ComponentSpec.class, RegisterBinaryFactories.class);
        }

        @Defaults
        void collectBinaries(BinaryContainer binaries, ComponentSpecContainer componentSpecs) {
            for (ComponentSpec componentSpec : componentSpecs.values()) {
                for (BinarySpec binary : componentSpec.getBinaries().values()) {
                    binaries.add(binary);
                }
            }
        }
    }
}
