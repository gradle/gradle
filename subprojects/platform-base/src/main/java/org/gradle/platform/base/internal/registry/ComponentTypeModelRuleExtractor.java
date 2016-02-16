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
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.SourceComponentSpec;
import org.gradle.platform.base.VariantComponentSpec;
import org.gradle.platform.base.component.internal.ComponentSpecFactory;
import org.gradle.platform.base.plugins.ComponentBasePlugin;

import java.util.List;

public class ComponentTypeModelRuleExtractor extends TypeModelRuleExtractor<ComponentType, ComponentSpec, ComponentSpecFactory> {
    public ComponentTypeModelRuleExtractor(ModelSchemaStore schemaStore) {
        super("component", ComponentSpec.class, ModelReference.of(ComponentSpecFactory.class), schemaStore);
    }

    @Override
    protected List<? extends Class<?>> getPluginsRequiredForClass(Class<? extends ComponentSpec> publicType) {
        if (SourceComponentSpec.class.isAssignableFrom(publicType) || VariantComponentSpec.class.isAssignableFrom(publicType)) {
            return ImmutableList.of(ComponentModelBasePlugin.class);
        }
        return ImmutableList.of(ComponentBasePlugin.class);
    }
}
