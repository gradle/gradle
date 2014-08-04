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

package org.gradle.runtime.base.internal.registry;

import com.google.common.collect.Lists;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.internal.core.Inputs;
import org.gradle.model.internal.core.ModelMutator;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.inspect.MethodRuleDefinitionHandler;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.runtime.base.*;
import org.gradle.runtime.base.internal.DefaultComponentSpecIdentifier;
import org.gradle.runtime.base.library.DefaultLibrarySpec;

import java.util.List;

public class ComponentModelRuleDefinitionHandler implements MethodRuleDefinitionHandler {

    private Instantiator instantiator;

    public ComponentModelRuleDefinitionHandler(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public boolean isSatisfiedBy(MethodRuleDefinition element) {
        return element.getAnnotation(ComponentType.class) != null;
    }

    public String getDescription() {
        return "annotated with @ComponentType";
    }

    public void register(MethodRuleDefinition ruleDefinition, ModelRegistry modelRegistry, Object target) {
        Class<? extends LibrarySpec> type = readComponentType(ruleDefinition);
        Class<? extends DefaultLibrarySpec> implementation = determineImplementationType(ruleDefinition, type);

        registerComponentType(target, type, implementation, modelRegistry, ruleDefinition.getDescriptor());
    }

    private Class<? extends LibrarySpec> readComponentType(MethodRuleDefinition ruleDefinition) {
        if (ruleDefinition.getReferences().size() != 1) {
            throw new InvalidComponentModelException(String.format("ComponentType method must have a single parameter of type %s.", ComponentTypeBuilder.class.getSimpleName()));
        }
        ModelType<?> componentBuilderType = ruleDefinition.getReferences().get(0).getType();
        if (!ComponentTypeBuilder.class.isAssignableFrom(componentBuilderType.getRawClass())) {
            throw new InvalidComponentModelException(String.format("ComponentType method must have a single parameter of type %s.", ComponentTypeBuilder.class.getSimpleName()));
        }
        if (componentBuilderType.getTypeVariables().size() != 1) {
            throw new InvalidComponentModelException("ComponentTypeBuilder parameter must declare a type parameter (must be generified).");
        }
        Class<?> componentType = componentBuilderType.getTypeVariables().get(0).getRawClass();
        if (!LibrarySpec.class.isAssignableFrom(componentType)) {
            throw new InvalidComponentModelException(String.format("Component type '%s' must extend '%s'.", componentType.getSimpleName(), LibrarySpec.class.getSimpleName()));
        }
        if (componentType.equals(LibrarySpec.class)) {
            throw new InvalidComponentModelException(String.format("Component type must be a subtype of '%s'.", LibrarySpec.class.getSimpleName()));
        }
        return (Class<? extends LibrarySpec>) componentType;
    }

    private Class<? extends DefaultLibrarySpec> determineImplementationType(MethodRuleDefinition ruleDefinition, Class<? extends LibrarySpec> type) {
        MyComponentTypeBuilder builder = new MyComponentTypeBuilder();
        ruleDefinition.getRuleInvoker().invoke(builder);
        Class<? extends LibrarySpec> implementation = builder.implementation;
        if (implementation == null) {
            throw new InvalidComponentModelException("ComponentType method must set default implementation.");
        }
        if (!DefaultLibrarySpec.class.isAssignableFrom(implementation)) {
            throw new InvalidComponentModelException(String.format("Component implementation '%s' must extend '%s'.", implementation.getSimpleName(), DefaultLibrarySpec.class.getSimpleName()));
        }
        if (!type.isAssignableFrom(implementation)) {
            throw new InvalidComponentModelException(String.format("Component implementation '%s' must implement '%s'.", implementation.getSimpleName(), type.getSimpleName()));
        }
        try {
            implementation.getConstructor();
        } catch (NoSuchMethodException nsmException) {
            throw new InvalidComponentModelException(String.format("Component implementation '%s' must have public default constructor.", implementation.getSimpleName()));
        }
        return (Class<? extends DefaultLibrarySpec>) implementation;
    }

    private void registerComponentType(final Object target, final Class<? extends LibrarySpec> type, final Class<? extends DefaultLibrarySpec> implementation, ModelRegistry modelRegistry, ModelRuleDescriptor descriptor) {
        if (!(target instanceof Project)) {
            throw new UnsupportedOperationException(String.format("Cannot declare component type '%s' as the target '%s' is not a project", type.getName(), target));
        }

        final Project project = (Project) target;
        project.getPlugins().apply(ComponentModelBasePlugin.class);

        modelRegistry.mutate(new ComponentTypeModelMutator(descriptor, type, implementation));
    }

    private static class MyComponentTypeBuilder<T extends LibrarySpec> implements ComponentTypeBuilder<T> {
        Class<? extends T> implementation;

        public void setDefaultImplementation(Class<? extends T> implementation) {
            this.implementation = implementation;
        }
    }

    private class ComponentTypeModelMutator implements ModelMutator<ExtensionContainer> {
        private final ModelRuleDescriptor descriptor;
        private final ModelReference<ExtensionContainer> subject;
        private final List<ModelReference<?>> inputs = Lists.newArrayList();
        private final Class<? extends LibrarySpec> type;
        private final Class<? extends DefaultLibrarySpec> implementation;

        private ComponentTypeModelMutator(ModelRuleDescriptor descriptor, Class<? extends LibrarySpec> type, Class<? extends DefaultLibrarySpec> implementation) {
            this.descriptor = descriptor;
            this.type = type;
            this.implementation = implementation;

            subject = ModelReference.of("extensions", ExtensionContainer.class);
            final ModelReference<?> input = ModelReference.of(ProjectIdentifier.class);
            inputs.add(input);
        }

        public ModelReference<ExtensionContainer> getSubject() {
            return subject;
        }

        public List<ModelReference<?>> getInputs() {
            return inputs;
        }

        public void mutate(ExtensionContainer extensions, Inputs inputs) {
            final ProjectSourceSet projectSourceSet = extensions.getByType(ProjectSourceSet.class);
            ComponentSpecContainer componentSpecs = extensions.getByType(ComponentSpecContainer.class);
            final ProjectIdentifier projectIdentifier = inputs.get(0, ModelType.of(ProjectIdentifier.class)).getInstance();
            componentSpecs.registerFactory(type, new NamedDomainObjectFactory() {
                public Object create(String name) {
                    FunctionalSourceSet componentSourceSet = projectSourceSet.maybeCreate(name);
                    ComponentSpecIdentifier id = new DefaultComponentSpecIdentifier(projectIdentifier.getPath(), name);
                    return DefaultLibrarySpec.create(implementation, id, componentSourceSet, instantiator);
                }
            });
        }

        public ModelRuleDescriptor getDescriptor() {
            return descriptor;
        }
    }
}
