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

package org.gradle.platform.base.component.internal;

import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.typeregistration.BaseInstanceFactory;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ComponentSpecIdentifier;
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier;

public class ComponentSpecFactory extends BaseInstanceFactory<ComponentSpec> {
    public ComponentSpecFactory(final ProjectIdentifier projectIdentifier) {
        super(ComponentSpec.class);
        registerFactory(DefaultComponentSpec.class, new ImplementationFactory<ComponentSpec, DefaultComponentSpec>() {
            @Override
            public <T extends DefaultComponentSpec> T create(ModelType<? extends ComponentSpec> publicType, ModelType<T> implementationType, String name, MutableModelNode componentNode) {
                ComponentSpecIdentifier id = new DefaultComponentSpecIdentifier(projectIdentifier.getPath(), name);
                return DefaultComponentSpec.create(publicType.getConcreteClass(), implementationType.getConcreteClass(), id, componentNode);
            }
        });
    }
}
