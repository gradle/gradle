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
import org.gradle.api.Action;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.internal.core.Inputs;
import org.gradle.model.internal.core.ModelMutator;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.inspect.MethodRuleDefinitionHandler;
import org.gradle.model.internal.inspect.RuleSourceDependencies;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.runtime.base.InvalidComponentModelException;
import org.gradle.runtime.base.TypeBuilder;

import java.util.List;

public abstract class AbstractAnnotationModelRuleDefinitionHandler<T, U> implements MethodRuleDefinitionHandler {

    protected String modelName;
    private final Class annotationClass;
    private Class<?> baseInterface;
    private Class<?> baseImplementation;
    private Class<? extends TypeBuilder> builderInterface;

    public AbstractAnnotationModelRuleDefinitionHandler(String modelName, Class annotationClass, Class<?> baseInterface, Class<?> baseImplementation, Class<? extends TypeBuilder> builderInterface){
        this.modelName = modelName;
        this.annotationClass = annotationClass;
        this.baseInterface = baseInterface;
        this.baseImplementation = baseImplementation;
        this.builderInterface = builderInterface;
    }

    public boolean isSatisfiedBy(MethodRuleDefinition element) {
        return element.getAnnotation(annotationClass) != null;
    }

    public String getDescription() {
        return String.format("annotated with @%s", annotationClass.getSimpleName());
    }

    abstract protected Action<MutationActionParameter> createMutationAction(Class<? extends T> type, Class<? extends U> implementation);
    abstract protected TypeBuilder createBuilder();

    public void register(MethodRuleDefinition ruleDefinition, ModelRegistry modelRegistry, RuleSourceDependencies dependencies) {
        try {
            Class<? extends T> type = readType(ruleDefinition);
            Class<? extends U> implementation = determineImplementationType(ruleDefinition, type, baseImplementation);

            dependencies.add(ComponentModelBasePlugin.class);
            if (implementation != null) {
                modelRegistry.mutate(new RegisterTypeRule(ruleDefinition.getDescriptor(), createMutationAction(type, implementation)));
            }
        } catch (InvalidComponentModelException e) {
            invalidModelRule(ruleDefinition, e);
        }
    }

    protected Class<? extends T> readType(MethodRuleDefinition ruleDefinition) {
        if (ruleDefinition.getReferences().size() != 1) {
            throw new InvalidComponentModelException(String.format("%s method must have a single parameter of type %s.", annotationClass.getSimpleName(), builderInterface.getSimpleName()));
        }
        if (!ModelType.of(Void.TYPE).equals(ruleDefinition.getReturnType())) {
            throw new InvalidComponentModelException(String.format("%s method must not have a return value.", annotationClass.getSimpleName()));
        }
        ModelType<?> builder = ruleDefinition.getReferences().get(0).getType();
        if (!builderInterface.isAssignableFrom(builder.getRawClass())) {
            throw new InvalidComponentModelException(String.format("%s method must have a single parameter of type %s.", annotationClass.getSimpleName(), builderInterface.getSimpleName()));
        }
        if (builder.getTypeVariables().size() != 1) {
            throw new InvalidComponentModelException(String.format("%s parameter must declare a type parameter.", builderInterface.getSimpleName()));
        }
        ModelType<?> modelType = builder.getTypeVariables().get(0);
        Class<?> spec = modelType.getRawClass();
        if (!baseInterface.isAssignableFrom(spec) || spec.equals(baseInterface)) {
            throw new InvalidComponentModelException(String.format("%s type '%s' is not a concrete subtype of '%s'.", StringUtils.capitalize(modelName), modelType.toString(), baseInterface.getSimpleName()));
        }
        return (Class<? extends T>) spec;
    }


    protected void invalidModelRule(MethodRuleDefinition ruleDefinition, InvalidComponentModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(String.format(" is not a valid %s model rule method.", modelName));
        throw new InvalidModelRuleDeclarationException(sb.toString(), e);
    }

    protected Class<? extends U> determineImplementationType(MethodRuleDefinition ruleDefinition, Class<?> type, Class<?> baseImplementationClass) {
        TypeBuilder builder = createBuilder();
        ruleDefinition.getRuleInvoker().invoke(builder);
        Class<?> implementation = builder.getImplementation();
        if (implementation != null) {
            if (!baseImplementationClass.isAssignableFrom(implementation)) {
                throw new InvalidComponentModelException(String.format("%s implementation '%s' must extend '%s'.", StringUtils.capitalize(modelName), implementation.getSimpleName(), baseImplementationClass.getSimpleName()));
            }
            if (!type.isAssignableFrom(implementation)) {
                throw new InvalidComponentModelException(String.format("%s implementation '%s' must implement '%s'.", StringUtils.capitalize(modelName), implementation.getSimpleName(), type.getSimpleName()));
            }
            try {
                implementation.getConstructor();
            } catch (NoSuchMethodException nsmException) {
                throw new InvalidComponentModelException(String.format("%s implementation '%s' must have public default constructor.", StringUtils.capitalize(modelName), implementation.getSimpleName()));
            }
        }
        return (Class<? extends U>) implementation;
    }


    protected static class RegisterTypeRule implements ModelMutator<ExtensionContainer> {
        private final ModelRuleDescriptor descriptor;
        private final ModelReference<ExtensionContainer> subject;
        private final List<ModelReference<?>> inputs = Lists.newArrayList();

        private final Action<MutationActionParameter> mutationAction;

        protected RegisterTypeRule(ModelRuleDescriptor descriptor, Action<MutationActionParameter> mutationAction) {
            this.descriptor = descriptor;
            this.mutationAction = mutationAction;

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
            mutationAction.execute(new MutationActionParameter(extensions, inputs));
        }

        public ModelRuleDescriptor getDescriptor() {
            return descriptor;
        }
    }


    protected static class MutationActionParameter {
        final ExtensionContainer extensions;
        final Inputs inputs;

        public MutationActionParameter(ExtensionContainer extensions, Inputs inputs){
            this.extensions = extensions;
            this.inputs = inputs;
        }
    }
}
