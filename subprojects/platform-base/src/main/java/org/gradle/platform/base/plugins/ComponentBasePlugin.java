/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.platform.base.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.model.Validate;
import org.gradle.model.internal.core.Hidden;
import org.gradle.model.internal.core.NodeInitializerRegistry;
import org.gradle.model.internal.manage.binding.StructBindingsStore;
import org.gradle.model.internal.manage.schema.extract.FactoryBasedStructNodeInitializerExtractionStrategy;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.platform.base.component.internal.ComponentSpecFactory;
import org.gradle.platform.base.component.internal.DefaultComponentSpec;
import org.gradle.platform.base.internal.ComponentSpecInternal;

/**
 * Base plugin for {@link ComponentSpec} support.
 *
 * - Registers the infrastructure to support the base {@link ComponentSpec} type and extensions to this type.
 */
@Incubating
public class ComponentBasePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class PluginRules extends RuleSource {
        @Model
        void components(ComponentSpecContainer componentSpecs) {
        }

        @Hidden
        @Model
        ComponentSpecFactory componentSpecFactory(ProjectIdentifier projectIdentifier, Instantiator instantiator, ITaskFactory taskFactory, SourceDirectorySetFactory sourceDirectorySetFactory) {
            return new ComponentSpecFactory(projectIdentifier, instantiator, taskFactory, sourceDirectorySetFactory);
        }

        @ComponentType
        void registerComponentSpec(TypeBuilder<ComponentSpec> builder) {
            builder.defaultImplementation(DefaultComponentSpec.class);
            builder.internalView(ComponentSpecInternal.class);
        }

        @Mutate
        void registerNodeInitializerExtractors(NodeInitializerRegistry nodeInitializerRegistry, ComponentSpecFactory componentSpecFactory, StructBindingsStore bindingsStore) {
            nodeInitializerRegistry.registerStrategy(new FactoryBasedStructNodeInitializerExtractionStrategy<ComponentSpec>(componentSpecFactory, bindingsStore));
        }

        @Validate
        void validateComponentSpecRegistrations(ComponentSpecFactory instanceFactory) {
            instanceFactory.validateRegistrations();
        }
    }
}
