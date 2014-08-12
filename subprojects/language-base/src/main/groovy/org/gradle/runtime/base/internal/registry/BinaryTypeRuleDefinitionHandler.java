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
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.internal.reflect.Instantiator;
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
import org.gradle.runtime.base.binary.DefaultBinarySpec;
import org.gradle.runtime.base.internal.BinaryNamingScheme;
import org.gradle.runtime.base.internal.DefaultBinaryNamingSchemeBuilder;

import java.util.List;

/**
 * TODO: extract more common code with ComponentModelRuleDefinitionHandler into AbstractAnnotationModelRuleDefinitionHandler
 * */
public class BinaryTypeRuleDefinitionHandler extends AbstractAnnotationModelRuleDefinitionHandler {

    private Instantiator instantiator;

    public BinaryTypeRuleDefinitionHandler(Instantiator instantiator) {
        super(BinaryType.class);
        this.instantiator = instantiator;
    }

    public void register(MethodRuleDefinition ruleDefinition, ModelRegistry modelRegistry, RuleSourceDependencies dependencies) {
        try {
            Class<? extends BinarySpec> type = readBinaryType(ruleDefinition);
            Class<? extends DefaultBinarySpec> implementation = determineImplementationType(ruleDefinition, type);

            /**
             * TODO languageBasePlugin should be enough here, debug.
             * */
            dependencies.add(ComponentModelBasePlugin.class);
            if (implementation != null) {
                modelRegistry.mutate(new RegisterBinaryTypeRule(ruleDefinition.getDescriptor(), type, implementation));
            }
        } catch (InvalidComponentModelException e) {
            invalidBinaryModelRule(ruleDefinition, e);
        }
    }

    private void invalidBinaryModelRule(MethodRuleDefinition ruleDefinition, InvalidComponentModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(" is not a valid binary model rule method.");
        throw new InvalidModelRuleDeclarationException(sb.toString(), e);
    }

    private Class<? extends BinarySpec> readBinaryType(MethodRuleDefinition ruleDefinition) {
        if (ruleDefinition.getReferences().size() != 1) {
            throw new InvalidComponentModelException(String.format("BinaryType method must have a single parameter of type %s.", BinaryTypeBuilder.class.getSimpleName()));
        }
        if (!ModelType.of(Void.TYPE).equals(ruleDefinition.getReturnType())) {
            throw new InvalidComponentModelException("BinaryType method must not have a return value.");
        }
        ModelType<?> binaryTypeBuilder = ruleDefinition.getReferences().get(0).getType();
        if (!BinaryTypeBuilder.class.isAssignableFrom(binaryTypeBuilder.getRawClass())) {
            throw new InvalidComponentModelException(String.format("BinaryType method must have a single parameter of type %s.", BinaryTypeBuilder.class.getSimpleName()));
        }
        if (binaryTypeBuilder.getTypeVariables().size() != 1) {
            throw new InvalidComponentModelException("BinaryTypeBuilder parameter must declare a type parameter (must be generified).");
        }
        Class<?> binarySpec = binaryTypeBuilder.getTypeVariables().get(0).getRawClass();
        if (!BinarySpec.class.isAssignableFrom(binarySpec)) {
            throw new InvalidComponentModelException(String.format("Binary type '%s' must extend '%s'.", binarySpec.getSimpleName(), BinarySpec.class.getSimpleName()));
        }
        if (binarySpec.equals(BinarySpec.class)) {
            throw new InvalidComponentModelException(String.format("Binary type must be a subtype of '%s'.", BinarySpec.class.getSimpleName()));
        }
        return (Class<? extends BinarySpec>) binarySpec;
    }

    private Class<? extends DefaultBinarySpec> determineImplementationType(MethodRuleDefinition ruleDefinition, Class<? extends BinarySpec> type) {
        MyBinaryTypeBuilder builder = new MyBinaryTypeBuilder();
        ruleDefinition.getRuleInvoker().invoke(builder);
        Class<? extends BinarySpec> implementation = builder.implementation;
        if (implementation != null) {
            if (!DefaultBinarySpec.class.isAssignableFrom(implementation)) {
                throw new InvalidComponentModelException(String.format("Binary implementation '%s' must extend '%s'.", implementation.getSimpleName(), DefaultBinarySpec.class.getSimpleName()));
            }
            if (!type.isAssignableFrom(implementation)) {
                throw new InvalidComponentModelException(String.format("Binary implementation '%s' must implement '%s'.", implementation.getSimpleName(), type.getSimpleName()));
            }
            try {
                implementation.getConstructor();
            } catch (NoSuchMethodException nsmException) {
                throw new InvalidComponentModelException(String.format("Binary implementation '%s' must have public default constructor.", implementation.getSimpleName()));
            }
        }
        return (Class<? extends DefaultBinarySpec>) implementation;
    }

    private static class MyBinaryTypeBuilder<T extends BinarySpec> implements BinaryTypeBuilder<T> {
        Class<? extends T> implementation;

        public void setDefaultImplementation(Class<? extends T> implementation) {
            if (this.implementation != null) {
                throw new InvalidComponentModelException("BinaryType method cannot set default implementation multiple times.");
            }
            this.implementation = implementation;
        }
    }

    private class RegisterBinaryTypeRule implements ModelMutator<ExtensionContainer> {
        private final ModelRuleDescriptor descriptor;
        private final ModelReference<ExtensionContainer> subject;
        private final List<ModelReference<?>> inputs = Lists.newArrayList();
        private final Class<? extends BinarySpec> type;
        private final Class<? extends DefaultBinarySpec> implementation;

        private RegisterBinaryTypeRule(ModelRuleDescriptor descriptor, Class<? extends BinarySpec> type, Class<? extends DefaultBinarySpec> implementation) {
            this.descriptor = descriptor;
            this.type = type;
            this.implementation = implementation;

            subject = ModelReference.of("extensions", ExtensionContainer.class);
        }

        public ModelReference<ExtensionContainer> getSubject() {
            return subject;
        }

        public List<ModelReference<?>> getInputs() {
            return inputs;
        }

        public void mutate(ExtensionContainer extensions, Inputs inputs) {
            BinaryContainer binaries = extensions.getByType(BinaryContainer.class);
            binaries.registerFactory(type, new NamedDomainObjectFactory() {
                public Object create(String name) {
                    BinaryNamingScheme binaryNamingScheme = new DefaultBinaryNamingSchemeBuilder()
                            .withComponentName(name)
                            .build();
                    return DefaultBinarySpec.create(implementation, binaryNamingScheme, instantiator);
                }
            });
        }

        public ModelRuleDescriptor getDescriptor() {
            return descriptor;
        }
    }
}

