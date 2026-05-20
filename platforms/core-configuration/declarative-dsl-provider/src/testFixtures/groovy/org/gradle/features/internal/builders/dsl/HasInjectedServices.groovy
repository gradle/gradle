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

package org.gradle.features.internal.builders.dsl

import org.gradle.features.internal.builders.ServiceDeclaration

/**
 * Contributes {@code injectedService(...)} backed by {@link #getInjectedServices()}.
 */
trait HasInjectedServices {
    abstract List<ServiceDeclaration> getInjectedServices()

    /** Adds an {@code @Inject} service accessor. */
    void injectedService(String name, Class type) {
        injectedServices.add(new ServiceDeclaration(name: name, type: type))
    }
}
