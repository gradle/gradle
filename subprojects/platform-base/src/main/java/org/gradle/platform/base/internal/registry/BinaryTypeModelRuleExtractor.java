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
import org.gradle.api.Action;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryType;
import org.gradle.platform.base.BinaryTypeBuilder;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.binary.internal.BinarySpecFactory;
import org.gradle.platform.base.internal.builder.TypeBuilderFactory;
import org.gradle.platform.base.internal.builder.TypeBuilderInternal;

import java.util.Set;

public class BinaryTypeModelRuleExtractor extends TypeModelRuleExtractor<BinaryType, BinarySpec, BaseBinarySpec> {
    public BinaryTypeModelRuleExtractor(ModelSchemaStore schemaStore) {
        super("binary", BinarySpec.class, BaseBinarySpec.class, BinaryTypeBuilder.class, schemaStore, new TypeBuilderFactory<BinarySpec>() {
            @Override
            public TypeBuilderInternal<BinarySpec> create(ModelSchema<? extends BinarySpec> schema) {
                return new DefaultBinaryTypeBuilder(schema);
            }
        });
    }

    @Override
    protected <R, S> ExtractedModelRule createRegistration(MethodRuleDefinition<R, S> ruleDefinition, ModelType<? extends BinarySpec> type, TypeBuilderInternal<BinarySpec> builder) {
        ImmutableList<Class<?>> dependencies = ImmutableList.<Class<?>>of(ComponentModelBasePlugin.class);
        ModelType<? extends BaseBinarySpec> implementation = determineImplementationType(type, builder);
        ModelAction registrationAction = createRegistrationAction(type, implementation, builder.getInternalViews(), ruleDefinition.getDescriptor());
        return new ExtractedModelAction(ModelActionRole.Defaults, dependencies, registrationAction);
    }

    private <S extends BinarySpec> ModelAction createRegistrationAction(final ModelType<S> publicType, final ModelType<? extends BaseBinarySpec> implementationType,
                                                                        final Set<Class<?>> internalViews, final ModelRuleDescriptor descriptor) {
        return NoInputsModelAction.of(ModelReference.of(BinarySpecFactory.class), descriptor, new Action<BinarySpecFactory>() {
            @Override
            public void execute(BinarySpecFactory binaries) {
                binaries.register(publicType, implementationType, internalViews, descriptor);
            }
        });
    }

    public static class DefaultBinaryTypeBuilder extends AbstractTypeBuilder<BinarySpec> implements BinaryTypeBuilder<BinarySpec> {
        public DefaultBinaryTypeBuilder(ModelSchema<? extends BinarySpec> schema) {
            super(BinaryType.class, schema);
        }
    }
}
