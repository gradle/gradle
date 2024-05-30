/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.api.NonNullApi;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Executes internal side effects of a build action while ensuring
 * they will be re-executed in case the build action result is loaded from cache.
 *
 * @see BuildTreeModelSideEffect
 */
@NonNullApi
@ServiceScope(Scope.BuildTree.class)
public interface BuildTreeModelSideEffectExecutor {

    <T> void runSideEffect(Class<? extends BuildTreeModelSideEffect<T>> implementation, T parameter);

}
