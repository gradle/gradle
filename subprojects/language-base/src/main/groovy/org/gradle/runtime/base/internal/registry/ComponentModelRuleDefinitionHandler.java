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
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.internal.core.Inputs;
import org.gradle.model.internal.core.ModelMutator;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.inspect.RuleSourceDependencies;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.runtime.base.*;
import org.gradle.runtime.base.component.DefaultComponentSpec;
import org.gradle.runtime.base.internal.DefaultComponentSpecIdentifier;

import java.util.List;

public class ComponentModelRuleDefinitionHandler extends AbstractAnnotationModelRuleDefinitionHandler {

    private Instantiator instantiator;

    public ComponentModelRuleDefinitionHandler(Instantiator instantiator) {
        super(ComponentType.class);
        this.instantiator = instantiator;
    }

    public void register(MethodRuleDefinition ruleDefinition, ModelRegistry modelRegistry, RuleSourceDependencies dependencies) {
        try {
            Class<? extends ComponentSpec> type = readComponentType(ruleDefinition);
            Class<? extends DefaultComponentSpec> implementation = determineImplementationType(ruleDefinition, type);

            dependencies.add(ComponentModelBasePlugin.class);

            if (implementation != null) {
                modelRegistry.mutate(new RegisterComponentTypeRule(ruleDefinition.getDescriptor(), type, implementation));
            }
        } catch (InvalidComponentModelException e) {
            invalidComponentModelRule(ruleDefinition, e);
        }
    }

    private void invalidComponentModelRule(MethodRuleDefinition ruleDefinition, InvalidComponentModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(" is not a valid component model rule method.");
        throw new InvalidModelRuleDeclarationException(sb.toString(), e);
    }

    private Class<? extends ComponentSpec> readComponentType(MethodRuleDefinition ruleDefinition) {
        if (ruleDefinition.getReferences().size() != 1) {
            throw new InvalidComponentModelException(String.format("ComponentType method must have a single parameter of type %s.", ComponentTypeBuilder.class.getSimpleName()));
        }
        if (!ModelType.of(Void.TYPE).equals(ruleDefinition.getReturnType())) {
            throw new InvalidComponentModelException("ComponentType method must not have a return value.");
        }
        ModelType<?> componentBuilderType = ruleDefinition.getReferences().get(0).getType();
        if (!ComponentTypeBuilder.class.isAssignableFrom(componentBuilderType.getRawClass())) {
            throw new InvalidComponentModelException(String.format("ComponentType method must have a single parameter of type %s.", ComponentTypeBuilder.class.getSimpleName()));
        }
        if (componentBuilderType.getTypeVariables().size() != 1) {
            throw new InvalidComponentModelException("ComponentTypeBuilder parameter must declare a type parameter (must be generified).");
        }
        Class<?> componentType = componentBuilderType.getTypeVariables().get(0).getRawClass();
        if (!ComponentSpec.class.isAssignableFrom(componentType)) {
            throw new InvalidComponentModelException(String.format("Component type '%s' must extend '%s'.", componentType.getSimpleName(), ComponentSpec.class.getSimpleName()));
        }
        if (componentType.equals(ComponentSpec.class)) {
            throw new InvalidComponentModelException(String.format("Component type must be a subtype of '%s'.", ComponentSpec.class.getSimpleName()));
        }
        return (Class<? extends ComponentSpec>) componentType;
    }

    private Class<? extends DefaultComponentSpec> determineImplementationType(MethodRuleDefinition ruleDefinition, Class<? extends ComponentSpec> type) {
        MyComponentTypeBuilder builder = new MyComponentTypeBuilder();
        ruleDefinition.getRuleInvoker().invoke(builder);
        Class<? extends ComponentSpec> implementation = builder.implementation;
        if (implementation != null) {
            if (!DefaultComponentSpec.class.isAssignableFrom(implementation)) {
                throw new InvalidComponentModelException(String.format("Component implementation '%s' must extend '%s'.", implementation.getSimpleName(), DefaultComponentSpec.class.getSimpleName()));
            }
            if (!type.isAssignableFrom(implementation)) {
                throw new InvalidComponentModelException(String.format("Component implementation '%s' must implement '%s'.", implementation.getSimpleName(), type.getSimpleName()));
            }
            try {
                implementation.getConstructor();
            } catch (NoSuchMethodException nsmException) {
                throw new InvalidComponentModelException(String.format("Component implementation '%s' must have public default constructor.", implementation.getSimpleName()));
            }
        }
        return (Class<? extends DefaultComponentSpec>) implementation;
    }

    private static class MyComponentTypeBuilder<T extends ComponentSpec> implements ComponentTypeBuilder<T> {
        Class<? extends T> implementation;

        public void setDefaultImplementation(Class<? extends T> implementation) {
            if (this.implementation != null) {
                throw new InvalidComponentModelException("ComponentType method cannot set default implementation multiple times.");
            }
            this.implementation = implementation;
        }
    }

    private class RegisterComponentTypeRule implements ModelMutator<ExtensionContainer> {
        private final ModelRuleDescriptor descriptor;
        private final ModelReference<ExtensionContainer> subject;
        private final List<ModelReference<?>> inputs = Lists.newArrayList();
        private final Class<? extends ComponentSpec> type;
        private final Class<? extends DefaultComponentSpec> implementation;

        private RegisterComponentTypeRule(ModelRuleDescriptor descriptor, Class<? extends ComponentSpec> type, Class<? extends DefaultComponentSpec> implementation) {
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
                    return DefaultComponentSpec.create(implementation, id, componentSourceSet, instantiator);
                }
            });
        }

        public ModelRuleDescriptor getDescriptor() {
            return descriptor;
        }
    }
}
