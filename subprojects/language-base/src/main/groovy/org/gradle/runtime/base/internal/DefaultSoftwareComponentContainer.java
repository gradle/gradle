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

package org.gradle.runtime.base.internal;

import org.gradle.api.Named;
import org.gradle.api.Namer;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.runtime.base.SoftwareComponent;
import org.gradle.runtime.base.SoftwareComponentContainer;

public class DefaultSoftwareComponentContainer extends DefaultPolymorphicDomainObjectContainer<SoftwareComponent> implements SoftwareComponentContainer {

    public DefaultSoftwareComponentContainer(Instantiator instantiator) {
        super(SoftwareComponent.class, instantiator, new NamedNamer());
    }

    // TODO:DAZ Not sure if SoftwareComponent should directly extend Named?
    private static class NamedNamer implements Namer<SoftwareComponent> {
        public String determineName(SoftwareComponent component) {
            if (component instanceof Named) {
                return ((Named) component).getName();
            }
            throw new IllegalArgumentException(String.format("Component %s cannot be added to %s as it is does not implement %s.",
                    component, SoftwareComponentContainer.class.getSimpleName(), Named.class.getSimpleName()));
        }
    }
}
