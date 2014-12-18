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

package org.gradle.platform.base.internal.registry;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.internal.Factory;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.inspect.RuleSourceDependencies;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.InvalidModelException;
import org.gradle.platform.base.internal.rules.RuleContext;

import java.lang.annotation.Annotation;
import java.util.List;

public abstract class TypeRuleDefinitionHandler<A extends Annotation, T, U extends T> extends AbstractAnnotationDrivenMethodComponentRuleDefinitionHandler<A> {

    private final String modelName;
    private final ModelType<T> baseInterface;
    private final ModelType<U> baseImplementation;
    private final ModelType<?> builderInterface;
    private final Factory<? extends TypeBuilderInternal<T>> typeBuilderFactory;

    public TypeRuleDefinitionHandler(String modelName, Class<T> baseInterface, Class<U> baseImplementation, Class<?> builderInterface, Factory<? extends TypeBuilderInternal<T>> typeBuilderFactory) {
        this.modelName = modelName;
        this.typeBuilderFactory = typeBuilderFactory;
        this.baseInterface = ModelType.of(baseInterface);
        this.baseImplementation = ModelType.of(baseImplementation);
        this.builderInterface = ModelType.of(builderInterface);
    }

    public <R> void register(MethodRuleDefinition<R> ruleDefinition, ModelRegistry modelRegistry, RuleSourceDependencies dependencies) {
        try {
            ModelType<? extends T> type = readType(ruleDefinition);

            TypeBuilderInternal<T> builder = typeBuilderFactory.create();
            ruleDefinition.getRuleInvoker().invoke(builder);


            doRegister(ruleDefinition, modelRegistry, dependencies, type, builder);
        } catch (InvalidModelException e) {
            invalidModelRule(ruleDefinition, e);
        }
    }

    abstract <R> void doRegister(MethodRuleDefinition<R> ruleDefinition, ModelRegistry modelRegistry, RuleSourceDependencies dependencies, ModelType<? extends T> type, TypeBuilderInternal<T> builder);


    protected ModelType<? extends T> readType(MethodRuleDefinition<?> ruleDefinition) {
        assertIsVoidMethod(ruleDefinition);
        if (ruleDefinition.getReferences().size() != 1) {
            throw new InvalidModelException(String.format("Method %s must have a single parameter of type '%s'.", getDescription(), builderInterface.toString()));
        }
        ModelType<?> builder = ruleDefinition.getReferences().get(0).getType();
        if (!builderInterface.isAssignableFrom(builder)) {
            throw new InvalidModelException(String.format("Method %s must have a single parameter of type '%s'.", getDescription(), builderInterface.toString()));
        }
        if (builder.getTypeVariables().size() != 1) {
            throw new InvalidModelException(String.format("Parameter of type '%s' must declare a type parameter.", builderInterface.toString()));
        }
        ModelType<?> subType = builder.getTypeVariables().get(0);

        if (subType.isWildcard()) {
            throw new InvalidModelException(String.format("%s type '%s' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.).", StringUtils.capitalize(modelName), subType.toString()));
        }

        ModelType<? extends T> asSubclass = baseInterface.asSubclass(subType);
        if (asSubclass == null) {
            throw new InvalidModelException(String.format("%s type '%s' is not a subtype of '%s'.", StringUtils.capitalize(modelName), subType.toString(), baseInterface.toString()));
        }

        return asSubclass;
    }


    protected void invalidModelRule(MethodRuleDefinition<?> ruleDefinition, InvalidModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(String.format(" is not a valid %s model rule method.", modelName));
        throw new InvalidModelRuleDeclarationException(sb.toString(), e);
    }

    protected ModelType<? extends U> determineImplementationType(ModelType<? extends T> type, TypeBuilderInternal<T> builder) {
        Class<? extends T> implementation = builder.getDefaultImplementation();
        if (implementation == null) {
            return null;
        }

        ModelType<? extends T> implementationType = ModelType.of(implementation);
        ModelType<? extends U> asSubclass = baseImplementation.asSubclass(implementationType);

        if (asSubclass == null) {
            throw new InvalidModelException(String.format("%s implementation '%s' must extend '%s'.", StringUtils.capitalize(modelName), implementationType, baseImplementation));
        }

        if (!type.isAssignableFrom(asSubclass)) {
            throw new InvalidModelException(String.format("%s implementation '%s' must implement '%s'.", StringUtils.capitalize(modelName), asSubclass, type));
        }

        try {
            asSubclass.getRawClass().getConstructor();
        } catch (NoSuchMethodException nsmException) {
            throw new InvalidModelException(String.format("%s implementation '%s' must have public default constructor.", StringUtils.capitalize(modelName), asSubclass));
        }

        return asSubclass;
    }

    protected static class RegistrationContext<T, U> {
        private final ModelType<? extends T> type;
        private final ModelType<? extends U> implementation;
        private final ExtensionContainer extensions;
        private final ProjectIdentifier projectIdentifier;

        public RegistrationContext(ModelType<? extends T> type, ModelType<? extends U> implementation, ExtensionContainer extensions, ProjectIdentifier projectIdentifier) {
            this.type = type;
            this.implementation = implementation;
            this.extensions = extensions;
            this.projectIdentifier = projectIdentifier;
        }

        public ModelType<? extends T> getType() {
            return type;
        }

        public ModelType<? extends U> getImplementation() {
            return implementation;
        }

        public ExtensionContainer getExtensions() {
            return extensions;
        }

        public ProjectIdentifier getProjectIdentifier() {
            return projectIdentifier;
        }
    }

    protected static class RegisterTypeRule<T, U> implements ModelMutator<ExtensionContainer> {
        private final ModelType<? extends T> type;
        private final ModelType<? extends U> implementation;
        private final ModelRuleDescriptor descriptor;
        private final ModelReference<ExtensionContainer> subject;
        private final List<ModelReference<?>> inputs;
        private final Action<? super RegistrationContext<T, U>> registerAction;

        protected RegisterTypeRule(ModelType<? extends T> type, ModelType<? extends U> implementation, ModelRuleDescriptor descriptor, Action<? super RegistrationContext<T, U>> registerAction) {
            this.type = type;
            this.implementation = implementation;
            this.descriptor = descriptor;
            this.registerAction = registerAction;

            subject = ModelReference.of("extensions", ExtensionContainer.class);
            inputs = ImmutableList.<ModelReference<?>>of(ModelReference.of(ProjectIdentifier.class));
        }

        public ModelReference<ExtensionContainer> getSubject() {
            return subject;
        }

        public List<ModelReference<?>> getInputs() {
            return inputs;
        }

        public ModelRuleDescriptor getDescriptor() {
            return descriptor;
        }

        public final void mutate(ModelNode modelNode, final ExtensionContainer extensionContainer, final Inputs inputs) {
            RuleContext.inContext(getDescriptor(), new Runnable() {
                public void run() {
                    RegistrationContext<T, U> context = new RegistrationContext<T, U>(type, implementation, extensionContainer, inputs.get(0, ModelType.of(ProjectIdentifier.class)).getInstance());
                    registerAction.execute(context);
                }
            });
        }
    }
}