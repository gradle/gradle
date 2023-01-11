/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.component;

import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.internal.reflect.Instantiator;

public class DefaultSoftwareComponentContainer extends DefaultNamedDomainObjectSet<SoftwareComponent> implements SoftwareComponentContainerInternal {

    private final Property<SoftwareComponent> mainComponent;

    public DefaultSoftwareComponentContainer(Instantiator instantiator, CollectionCallbackActionDecorator decorator, ObjectFactory objectFactory) {
        super(SoftwareComponentInternal.class, instantiator, decorator);
        mainComponent = objectFactory.property(SoftwareComponent.class);
    }

    @Override
    public Property<SoftwareComponent> getMainComponent() {
        return mainComponent;
    }
}
