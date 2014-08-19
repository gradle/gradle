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

import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.core.Inputs;
import org.gradle.model.internal.core.ModelMutator;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.runtime.base.*;
import org.gradle.runtime.base.binary.BaseBinarySpec;
import org.gradle.runtime.base.internal.BinaryNamingScheme;
import org.gradle.runtime.base.internal.DefaultBinaryNamingSchemeBuilder;

public class BinaryTypeRuleDefinitionHandler extends AbstractAnnotationModelRuleDefinitionHandler<BinarySpec, BaseBinarySpec> {

    private Instantiator instantiator;

    public BinaryTypeRuleDefinitionHandler(Instantiator instantiator) {
        super("binary", BinaryType.class, BinarySpec.class, BaseBinarySpec.class, BinaryTypeBuilder.class);
        this.instantiator = instantiator;
    }

    @Override
    protected ModelMutator<ExtensionContainer> createModelMutator(ModelRuleDescriptor descriptor, Class<? extends BinarySpec> type, Class<? extends BaseBinarySpec> implementation) {
        return new BinaryTypeRuleMutationAction(descriptor, instantiator, type, implementation);
    }

    @Override
    protected TypeBuilderInternal createBuilder() {
        return new DefaultBinaryTypeBuilder();
    }

    private static class DefaultBinaryTypeBuilder<T extends BinarySpec> extends AbstractTypeBuilder<T> implements BinaryTypeBuilder<T> {
        public DefaultBinaryTypeBuilder() {
            super(BinaryType.class);
        }
    }

    private static class BinaryTypeRuleMutationAction extends RegisterTypeRule {

        private final Instantiator instantiator;
        private final Class<? extends BinarySpec> type;
        private final Class<? extends BaseBinarySpec> implementation;

        public BinaryTypeRuleMutationAction(ModelRuleDescriptor descriptor, Instantiator instantiator, Class<? extends BinarySpec> type, Class<? extends BaseBinarySpec> implementation) {
            super(descriptor);
            this.instantiator = instantiator;
            this.type = type;
            this.implementation = implementation;
        }

        protected void doMutate(ExtensionContainer extensions, Inputs inputs) {
            BinaryContainer binaries = extensions.getByType(BinaryContainer.class);
            binaries.registerFactory(type, new NamedDomainObjectFactory() {
                public Object create(String name) {
                    BinaryNamingScheme binaryNamingScheme = new DefaultBinaryNamingSchemeBuilder()
                            .withComponentName(name)
                            .build();
                    return BaseBinarySpec.create(implementation, binaryNamingScheme, instantiator);
                }
            });
        }

    }
}

