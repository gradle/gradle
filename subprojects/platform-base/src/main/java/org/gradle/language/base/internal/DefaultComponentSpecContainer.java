/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.base.internal;

import org.gradle.internal.util.BiFunction;
import org.gradle.model.internal.core.DefaultCollectionBuilder;
import org.gradle.model.internal.core.ModelCreators;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ComponentSpecContainer;

public class DefaultComponentSpecContainer extends DefaultCollectionBuilder<ComponentSpec> implements ComponentSpecContainer {
    public DefaultComponentSpecContainer(ModelType<ComponentSpec> elementType, ModelRuleDescriptor sourceDescriptor, MutableModelNode modelNode, BiFunction<? extends ModelCreators.Builder, ? super MutableModelNode, ? super ModelReference<? extends ComponentSpec>> creatorFunction) {
        super(elementType, sourceDescriptor, modelNode, creatorFunction);
    }
}
