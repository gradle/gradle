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

package org.gradle.api.internal.project;

import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * A preferred way to create project wrappers.
 * The created wrapper is runtime-decorated and its lifetime is synchronized with the wrapped project.
 */
@ServiceScope(Scope.Build.class)
public interface ProjectWrapperFactory {

    <T extends ProjectInternal> T newWrapper(ProjectInternal wrapped, Class<T> type, Object... parameters) throws ObjectInstantiationException;
}
