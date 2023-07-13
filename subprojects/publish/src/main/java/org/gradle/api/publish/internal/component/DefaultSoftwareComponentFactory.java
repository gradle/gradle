/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.publish.internal.component;

import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.internal.reflect.Instantiator;

public class DefaultSoftwareComponentFactory implements SoftwareComponentFactory {

    private final Instantiator instantiator;

    public DefaultSoftwareComponentFactory(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    public AdhocComponentWithVariants adhoc(String name) {
        return instantiator.newInstance(DefaultAdhocSoftwareComponent.class, name, instantiator);
    }
}
