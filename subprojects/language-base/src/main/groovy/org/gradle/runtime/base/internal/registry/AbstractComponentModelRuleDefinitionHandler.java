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
import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.internal.core.Inputs;
import org.gradle.model.internal.core.ModelMutator;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.AbstractAnnotationDrivenMethodRuleDefinitionHandler;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.inspect.RuleSourceDependencies;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.runtime.base.InvalidComponentModelException;
import org.gradle.runtime.base.TypeBuilder;

import java.lang.annotation.Annotation;
import java.util.List;

// TODO:DAZ Convert to use ModelType throughout
public abstract class AbstractComponentModelRuleDefinitionHandler<A extends Annotation, T, U extends T> extends AbstractAnnotationDrivenMethodRuleDefinitionHandler<A> {

    protected String modelName;
    private final Class<A> annotationClass;
    private ModelType<T> baseInterface;
    private ModelType<U> baseImplementation;
    private ModelType<? extends TypeBuilder> builderInterface;

    public AbstractComponentModelRuleDefinitionHandler(String modelName, Class<A> annotationClass,
                                                       Class<T> baseInterface, Class<U> baseImplementation, Class<? extends TypeBuilder> builderInterface) {
        this.modelName = modelName;
        this.annotationClass = annotationClass;
        this.baseInterface = ModelType.of(baseInterface);
        this.baseImplementation = ModelType.of(baseImplementation);
        this.builderInterface = ModelType.of(builderInterface);
    }

    abstract protected ModelMutator<ExtensionContainer> createModelMutator(ModelRuleDescriptor descriptor, Class<? extends T> type, Class<? extends U> implementation);

    abstract protected TypeBuilderInternal createBuilder();

    public void register(MethodRuleDefinition<?> ruleDefinition, ModelRegistry modelRegistry, RuleSourceDependencies dependencies) {
        try {
            Class<? extends T> type = readType(ruleDefinition);
            Class<? extends U> implementation = determineImplementationType(ruleDefinition, type);

            dependencies.add(ComponentModelBasePlugin.class);
            if (implementation != null) {
                modelRegistry.mutate(createModelMutator(ruleDefinition.getDescriptor(), type, implementation));
            }
        } catch (InvalidComponentModelException e) {
            invalidModelRule(ruleDefinition, e);
        }
    }

    protected Class<? extends T> readType(MethodRuleDefinition<?> ruleDefinition) {
        if (!ModelType.of(Void.TYPE).equals(ruleDefinition.getReturnType())) {
            throw new InvalidComponentModelException(String.format("%s method must not have a return value.", annotationClass.getSimpleName()));
        }
        if (ruleDefinition.getReferences().size() != 1) {
            throw new InvalidComponentModelException(String.format("%s method must have a single parameter of type '%s'.", annotationClass.getSimpleName(), builderInterface.toString()));
        }
        ModelType<?> builder = ruleDefinition.getReferences().get(0).getType();
        if (!builderInterface.isAssignableFrom(builder)) {
            throw new InvalidComponentModelException(String.format("%s method must have a single parameter of type '%s'.", annotationClass.getSimpleName(), builderInterface.toString()));
        }
        if (builder.getTypeVariables().size() != 1) {
            throw new InvalidComponentModelException(String.format("Parameter of type '%s' must declare a type parameter.", builderInterface.toString()));
        }
        ModelType<?> subType = builder.getTypeVariables().get(0);
        if (!baseInterface.isAssignableFrom(subType) || subType.isAssignableFrom(baseInterface)) {
            throw new InvalidComponentModelException(String.format("%s type '%s' is not a concrete subtype of '%s'.", StringUtils.capitalize(modelName), subType.toString(), baseInterface.toString()));
        }
        // TODO:DAZ Propogate ModelType out
        return (Class<? extends T>) subType.getRawClass();
    }


    protected void invalidModelRule(MethodRuleDefinition<?> ruleDefinition, InvalidComponentModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(String.format(" is not a valid %s model rule method.", modelName));
        throw new InvalidModelRuleDeclarationException(sb.toString(), e);
    }

    protected Class<? extends U> determineImplementationType(MethodRuleDefinition<?> ruleDefinition, Class<? extends T> typeClass) {
        ModelType<? extends T> type = ModelType.of(typeClass);
        TypeBuilderInternal builder = createBuilder();
        ruleDefinition.getRuleInvoker().invoke(builder);
        Class<?> implementation = builder.getDefaultImplementation();
        if (implementation == null) {
            return null;
        }
        ModelType<?> implementationType = ModelType.of(implementation);
        if (!baseImplementation.isAssignableFrom(implementationType)) {
            throw new InvalidComponentModelException(String.format("%s implementation '%s' must extend '%s'.", StringUtils.capitalize(modelName), implementationType.toString(), baseImplementation.toString()));
        }
        if (!type.isAssignableFrom(implementationType)) {
            throw new InvalidComponentModelException(String.format("%s implementation '%s' must implement '%s'.", StringUtils.capitalize(modelName), implementationType.toString(), type.toString()));
        }
        try {
            implementation.getConstructor();
        } catch (NoSuchMethodException nsmException) {
            throw new InvalidComponentModelException(String.format("%s implementation '%s' must have public default constructor.", StringUtils.capitalize(modelName), implementationType.toString()));
        }
        // TODO:DAZ Propogate ModelType out
        return (Class<? extends U>) implementationType.getRawClass();
    }

    protected abstract static class RegisterTypeRule implements ModelMutator<ExtensionContainer> {
        private final ModelRuleDescriptor descriptor;
        private final ModelReference<ExtensionContainer> subject;
        private final List<ModelReference<?>> inputs = Lists.newArrayList();

        protected RegisterTypeRule(ModelRuleDescriptor descriptor) {
            this.descriptor = descriptor;

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

        public ModelRuleDescriptor getDescriptor() {
            return descriptor;
        }

        public final void mutate(final ExtensionContainer extensionContainer, final Inputs inputs) {
            RuleContext.inContext(getDescriptor(), new Runnable() {
                public void run() {
                    doMutate(extensionContainer, inputs);
                }
            });
        }

        protected abstract void doMutate(ExtensionContainer extensions, Inputs inputs);
    }
}
