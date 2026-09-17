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

package org.gradle.api.internal.model;

import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Factory for creating ObjectFactory instances.
 */
@ServiceScope(Scope.Project.class)
public interface ObjectFactoryFactory {
    /**
     * Creates a new ObjectFactory instance backed by the provided ServiceLookup.
     *
     * @param serviceLookup The service lookup to be used by the ObjectFactory.
     * @return A new ObjectFactory instance.
     */
    ObjectFactory createObjectFactory(ServiceLookup serviceLookup);
}
