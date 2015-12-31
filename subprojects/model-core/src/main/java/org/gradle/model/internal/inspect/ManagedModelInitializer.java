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

package org.gradle.model.internal.inspect;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import org.gradle.internal.BiAction;
import org.gradle.internal.typeconversion.TypeConverter;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.projection.ManagedModelProjection;
import org.gradle.model.internal.manage.schema.ManagedImplStructSchema;

import java.util.Arrays;
import java.util.List;

public class ManagedModelInitializer<T> extends AbstractManagedModelInitializer<T> {

    public ManagedModelInitializer(ManagedImplStructSchema<T> modelSchema) {
        super(modelSchema);
    }

    @Override
    public Multimap<ModelActionRole, ModelAction> getActions(ModelReference<?> subject, ModelRuleDescriptor descriptor) {
        return ImmutableSetMultimap.<ModelActionRole, ModelAction>builder()
            .put(ModelActionRole.Discover, DirectNodeInputUsingModelAction.of(subject, descriptor,
                Arrays.<ModelReference<?>>asList(
                    ModelReference.of(ManagedProxyFactory.class),
                    ModelReference.of(TypeConverter.class)
                ),
                new BiAction<MutableModelNode, List<ModelView<?>>>() {
                    @Override
                    public void execute(MutableModelNode mutableModelNode, List<ModelView<?>> modelViews) {
                        ManagedProxyFactory proxyFactory = ModelViews.getInstance(modelViews.get(0), ManagedProxyFactory.class);
                        TypeConverter typeConverter = ModelViews.getInstance(modelViews, 1, TypeConverter.class);
                        mutableModelNode.addProjection(new ManagedModelProjection<T>(schema, null, proxyFactory, typeConverter));
                    }
                }
            ))
            .put(ModelActionRole.Create, DirectNodeInputUsingModelAction.of(subject, descriptor,
                Arrays.<ModelReference<?>>asList(
                    ModelReference.of(NodeInitializerRegistry.class),
                    ModelReference.of(ManagedProxyFactory.class),
                    ModelReference.of(TypeConverter.class)
                ),
                new BiAction<MutableModelNode, List<ModelView<?>>>() {
                    @Override
                    public void execute(MutableModelNode modelNode, List<ModelView<?>> modelViews) {
                        NodeInitializerRegistry nodeInitializerRegistry = ModelViews.getInstance(modelViews, 0, NodeInitializerRegistry.class);
                        ManagedProxyFactory proxyFactory = ModelViews.getInstance(modelViews, 1, ManagedProxyFactory.class);
                        TypeConverter typeConverter = ModelViews.getInstance(modelViews, 2, TypeConverter.class);

                        addPropertyLinks(modelNode, nodeInitializerRegistry, proxyFactory, schema.getProperties(), typeConverter);
                    }
                }
            ))
            .build();
    }
}
