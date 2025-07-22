/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.instantiation.managed;

import org.gradle.internal.service.AnnotatedServiceLifecycleHandler;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

/**
 * Responsible for creating instances of {@link org.gradle.api.model.ManagedType managed types}.
 */
@ServiceScope({Scope.Global.class, Scope.BuildTree.class, Scope.Build.class, Scope.Project.class})
public interface ManagedObjectRegistry extends AnnotatedServiceLifecycleHandler {

    /**
     * Create a managed object instance for a type with no arguments.
     *
     * @return null if no zero-argument factory is registered for the type.
     */
    @Nullable
    <T> T newInstance(Class<T> type);

    /**
     * Create a managed object instance for a type with a type argument.
     *
     * @return null if no single-argument factory is registered for the type.
     */
    @Nullable
    <T> T newInstance(Class<T> type, Class<?> arg1);

    /**
     * Create a managed object instance for a type with two type arguments.
     *
     * @return null if no two-argument factory is registered for the type.
     */
    @Nullable
    <T> T newInstance(Class<T> type, Class<?> arg1, Class<?> arg2);

}
