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

package org.gradle.internal.problems.impl;

import org.gradle.initialization.ClassLoaderScopeOrigin;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a source file name back to the build script registered under it.
 */
@ServiceScope(Scope.BuildSession.class)
@NullMarked
interface RegisteredScripts {

    /**
     * The script registered under the given source file name, or {@code null} if none is.
     */
    ClassLoaderScopeOrigin.@Nullable Script scriptFor(String fileName);
}
