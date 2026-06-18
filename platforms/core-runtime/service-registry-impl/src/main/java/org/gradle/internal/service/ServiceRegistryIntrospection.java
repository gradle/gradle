/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.service;

import java.util.Set;

/**
 * Introspection over a {@link ServiceRegistry}, allowing the set of resolvable service types to be
 * enumerated without instantiating any service.
 * <p>
 * Implemented by {@link DefaultServiceRegistry}, so it is available on the scoped registries built from it
 * (e.g. the per-project registry returned by {@code ProjectInternal.getServices()}).
 * <p>
 * This is internal tooling — primarily for auditing which services are injectable into user code (a service
 * is injectable by any type it can be resolved as) and reconciling that against the documented set.
 */
public interface ServiceRegistryIntrospection {

    /**
     * Returns every type by which a service can be resolved from this registry <em>and all of its ancestor
     * registries</em> — that is, each registered service's declared types together with their supertypes and
     * interfaces. This is the set of types that {@link ServiceRegistry#find(java.lang.reflect.Type)} could
     * satisfy, and therefore the set of types injectable from this scope.
     * <p>
     * Only type metadata is read; no service instances are realized.
     */
    Set<Class<?>> getAllServiceTypes();
}
