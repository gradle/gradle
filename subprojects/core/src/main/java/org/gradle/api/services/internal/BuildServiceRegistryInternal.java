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

package org.gradle.api.services.internal;

import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistration;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.resources.SharedResource;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

@ServiceScope(Scope.Build.class)
public interface BuildServiceRegistryInternal extends BuildServiceRegistry {
    /**
     * @param maxUsages Same semantics as {@link SharedResource#getMaxUsages()}.
     */
    BuildServiceProvider<?, ?> register(String name, Class<? extends BuildService<?>> implementationType, @Nullable BuildServiceParameters parameters, int maxUsages);

    /**
     * Same as #register(name, implementationType, parameters, maxUsages), but conditional.
     *
     * @return the registered or already existing provider
     */
    BuildServiceProvider<?, ?> registerIfAbsent(String name, Class<? extends BuildService<?>> implementationType, @Nullable BuildServiceParameters parameters, int maxUsages);

    /**
     * Returns a shared build service provider that can lazily resolve to the service named and typed as given.
     */
    BuildServiceProvider<?, ?> consume(String name, Class<? extends BuildService<?>> implementationType);

    @Nullable
    SharedResource forService(BuildServiceProvider<?, ?> service);

    @Nullable
    BuildServiceRegistration<?, ?> findByName(String name);

    @Nullable
    BuildServiceRegistration<?, ?> findByType(Class<?> type);

    @Nullable
    BuildServiceRegistration<?, ?> findRegistration(Class<?> type, String name);

    Set<BuildServiceRegistration<?, ?>> findRegistrations(Class<?> type, String name);

    List<ResourceLock> getSharedResources(Set<Provider<? extends BuildService<?>>> services);
}
