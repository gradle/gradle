/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.execution;

import org.gradle.api.execution.SharedResource;
import org.gradle.api.execution.SharedResourceContainer;
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.internal.reflect.Instantiator;

public class DefaultSharedResourceContainer extends AbstractNamedDomainObjectContainer<SharedResource> implements SharedResourceContainer {

    public DefaultSharedResourceContainer(Instantiator instantiator) {
        super(SharedResource.class, instantiator, CollectionCallbackActionDecorator.NOOP);
    }

    @Override
    protected SharedResource doCreate(String name) {
        return getInstantiator().newInstance(DefaultSharedResource.class, name);
    }
}
