/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.component.CompositeSoftwareComponent;
import org.gradle.internal.reflect.Instantiator;

class CompositeSoftwareComponentFactory implements NamedDomainObjectFactory<CompositeSoftwareComponent> {
    private final Instantiator instantiator;

    public CompositeSoftwareComponentFactory(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    public CompositeSoftwareComponent create(String name) {
        return instantiator.newInstance(DefaultCompositeSoftwareComponent.class, name, instantiator.newInstance(DefaultSoftwareComponentContainer.class, instantiator));
    }
}
