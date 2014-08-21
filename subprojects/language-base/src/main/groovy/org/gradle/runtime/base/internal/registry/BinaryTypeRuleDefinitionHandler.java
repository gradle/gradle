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

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.core.ModelType;
import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.base.BinarySpec;
import org.gradle.runtime.base.BinaryType;
import org.gradle.runtime.base.BinaryTypeBuilder;
import org.gradle.runtime.base.binary.BaseBinarySpec;
import org.gradle.runtime.base.internal.BinaryNamingScheme;
import org.gradle.runtime.base.internal.DefaultBinaryNamingSchemeBuilder;

public class BinaryTypeRuleDefinitionHandler extends AbstractComponentModelRuleDefinitionHandler<BinaryType, BinarySpec, BaseBinarySpec> {

    private Instantiator instantiator;

    public BinaryTypeRuleDefinitionHandler(Instantiator instantiator) {
        super("binary", BinaryType.class, BinarySpec.class, BaseBinarySpec.class, BinaryTypeBuilder.class);
        this.instantiator = instantiator;
    }

    @Override
    protected <V extends BinarySpec, W extends BaseBinarySpec> Action<? super TypeRegistrationContext> createTypeRegistrationAction(final ModelType<V> type, final ModelType<W> implementation) {
        return new Action<TypeRegistrationContext>() {
            public void execute(TypeRegistrationContext typeRegistrationContext) {
                BinaryContainer binaries = typeRegistrationContext.getExtensions().getByType(BinaryContainer.class);
                binaries.registerFactory(type.getConcreteClass(), new NamedDomainObjectFactory<V>() {
                    public V create(String name) {
                        BinaryNamingScheme binaryNamingScheme = new DefaultBinaryNamingSchemeBuilder()
                                .withComponentName(name)
                                .build();

                        // safe because we implicitly know that U extends V, but can't express this in the type system
                        @SuppressWarnings("unchecked")
                        V created = (V) BaseBinarySpec.create(implementation.getConcreteClass(), binaryNamingScheme, instantiator);

                        return created;
                    }
                });
            }
        };
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

}

