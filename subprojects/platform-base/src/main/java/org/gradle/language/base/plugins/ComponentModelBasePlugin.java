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

import com.google.common.collect.Lists;
import org.gradle.api.*;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.text.TreeFormatter;
import org.gradle.language.base.internal.model.BinarySourceTransformations;
import org.gradle.language.base.internal.model.ComponentRules;
import org.gradle.language.base.internal.registry.DefaultLanguageTransformContainer;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.model.*;
import org.gradle.model.internal.core.Hidden;
import org.gradle.model.internal.core.NodeInitializerRegistry;
import org.gradle.model.internal.manage.binding.StructBindingsStore;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.extract.FactoryBasedStructNodeInitializerExtractionStrategy;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.*;
import org.gradle.platform.base.component.BaseComponentSpec;
import org.gradle.platform.base.component.internal.ComponentSpecFactory;
import org.gradle.platform.base.internal.*;
import org.gradle.platform.base.plugins.BinaryBasePlugin;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;

import static org.gradle.model.internal.core.ModelNodes.withType;
import static org.gradle.model.internal.core.NodePredicate.allDescendants;

/**
 * Base plugin for language support.
 *
 * Adds a {@link org.gradle.platform.base.ComponentSpecContainer} named {@code components} to the model.
 *
 * For each binary instance added to the binaries container, registers a lifecycle task to create that binary.
 */
@Incubating
public class ComponentModelBasePlugin implements Plugin<Project> {
    private final ModelRegistry modelRegistry;

    @Inject
    public ComponentModelBasePlugin(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    public void apply(Project project) {
        project.getPluginManager().apply(LanguageBasePlugin.class);
        project.getPluginManager().apply(BinaryBasePlugin.class);

        modelRegistry.getRoot().applyTo(allDescendants(withType(ComponentSpec.class)), ComponentRules.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @Hidden @Model
        ComponentSpecFactory componentSpecFactory(ProjectIdentifier projectIdentifier) {
            return new ComponentSpecFactory("components", projectIdentifier);
        }

        @ComponentType
        void registerBaseComponentSpec(ComponentTypeBuilder<ComponentSpec> builder) {
            builder.defaultImplementation(BaseComponentSpec.class);
            builder.internalView(ComponentSpecInternal.class);
        }

        @ComponentType
        void registerPlatformAwareComponet(ComponentTypeBuilder<PlatformAwareComponentSpec> builder) {
            builder.internalView(PlatformAwareComponentSpecInternal.class);
        }

        @Model
        void components(ComponentSpecContainer componentSpecs) {
        }

        @Mutate
        void registerNodeInitializerExtractors(NodeInitializerRegistry nodeInitializerRegistry, ComponentSpecFactory componentSpecFactory, ModelSchemaStore schemaStore, StructBindingsStore bindingsStore) {
            nodeInitializerRegistry.registerStrategy(new FactoryBasedStructNodeInitializerExtractionStrategy<ComponentSpec>(componentSpecFactory, schemaStore, bindingsStore));
        }

        @Hidden @Model
        LanguageTransformContainer languageTransforms() {
            return new DefaultLanguageTransformContainer();
        }

        // Finalizing here, as we need this to run after any 'assembling' task (jar, link, etc) is created.
        // TODO:DAZ Convert this to `@BinaryTasks` when we model a `NativeAssembly` instead of wiring compile tasks directly to LinkTask
        @Finalize
        void createSourceTransformTasks(final TaskContainer tasks, final ModelMap<BinarySpecInternal> binaries, LanguageTransformContainer languageTransforms, ServiceRegistry serviceRegistry) {
            BinarySourceTransformations transformations = new BinarySourceTransformations(tasks, languageTransforms, serviceRegistry);
            for (BinarySpecInternal binary : binaries) {
                if (binary.isLegacyBinary()) {
                    continue;
                }

                transformations.createTasksFor(binary);
            }
        }

        @Model
        PlatformContainer platforms(ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            return instantiator.newInstance(DefaultPlatformContainer.class, instantiator);
        }

        @Hidden @Model
        PlatformResolvers platformResolver(PlatformContainer platforms) {
            return new DefaultPlatformResolvers(platforms);
        }

        @Mutate
        void registerPlatformExtension(ExtensionContainer extensions, PlatformContainer platforms) {
            extensions.add("platforms", platforms);
        }

        @Mutate
        void collectBinaries(BinaryContainer binaries, ComponentSpecContainer componentSpecs) {
            for (ComponentSpec componentSpec : componentSpecs.values()) {
                for (BinarySpecInternal binary : componentSpec.getBinaries().withType(BinarySpecInternal.class).values()) {
                    binaries.put(binary.getProjectScopedName(), binary);
                }
            }
        }

        @Validate
        void validateComponentSpecRegistrations(ComponentSpecFactory instanceFactory) {
            instanceFactory.validateRegistrations();
        }

        @Mutate
        void attachBinariesToAssembleLifecycle(@Path("tasks.assemble") Task assemble, ComponentSpecContainer components) {
            List<BinarySpecInternal> notBuildable = Lists.newArrayList();
            boolean hasBuildableBinaries = false;
            for (ComponentSpec component : components) {
                for (BinarySpecInternal binary : component.getBinaries().withType(BinarySpecInternal.class)) {
                    if (binary.isBuildable()) {
                        assemble.dependsOn(binary);
                        hasBuildableBinaries = true;
                    } else {
                        notBuildable.add(binary);
                    }
                }
            }
            if (!hasBuildableBinaries && !notBuildable.isEmpty()) {
                assemble.doFirst(new CheckForNotBuildableBinariesAction(notBuildable));
            }
        }

        private static class CheckForNotBuildableBinariesAction implements Action<Task> {
            private final List<BinarySpecInternal> notBuildable;

            public CheckForNotBuildableBinariesAction(List<BinarySpecInternal> notBuildable) {
                this.notBuildable = notBuildable;
            }

            @Override
            public void execute(Task task) {
                Set<? extends Task> taskDependencies = task.getTaskDependencies().getDependencies(task);

                if (taskDependencies.isEmpty()) {
                    TreeFormatter formatter = new TreeFormatter();
                    formatter.node("No buildable binaries found");
                    formatter.startChildren();
                    for (BinarySpecInternal binary : notBuildable) {
                        formatter.node(binary.getDisplayName());
                        formatter.startChildren();
                        binary.getBuildAbility().explain(formatter);
                        formatter.endChildren();
                    }
                    formatter.endChildren();
                    throw new GradleException(formatter.toString());
                }
            }
        }
    }
}
