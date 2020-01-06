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

package org.gradle.internal.service.scopes;

import org.gradle.api.internal.provider.DefaultProviderFactory;
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory;
import org.gradle.api.internal.provider.ValueSourceProviderFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.snapshot.impl.DefaultValueSnapshotter;
import org.gradle.internal.state.ManagedFactoryRegistry;

public class WorkerSharedUserHomeScopeServices {

    ValueSourceProviderFactory createValueSourceProviderFactory(
        InstantiatorFactory instantiatorFactory,
        IsolatableFactory isolatableFactory,
        ServiceRegistry services,
        ListenerManager listenerManager
    ) {
        return new DefaultValueSourceProviderFactory(
            listenerManager,
            instantiatorFactory,
            isolatableFactory,
            services
        );
    }

    ProviderFactory createProviderFactory(ValueSourceProviderFactory valueSourceProviderFactory) {
        return new DefaultProviderFactory(valueSourceProviderFactory);
    }

    DefaultValueSnapshotter createValueSnapshotter(ClassLoaderHierarchyHasher classLoaderHierarchyHasher, ManagedFactoryRegistry managedFactoryRegistry) {
        return new DefaultValueSnapshotter(classLoaderHierarchyHasher, managedFactoryRegistry);
    }
}
