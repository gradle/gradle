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

/**
 * Base class for service registries that can be used as parents of {@link DefaultServiceRegistry}.
 * <p>
 * Subclasses provide access to their internal {@link ServiceProvider} via {@link #asServiceProvider()},
 * which child registries use to resolve services from their parents.
 */
abstract class AbstractServiceRegistry implements ServiceRegistry {
    abstract ServiceProvider asServiceProvider();
}
