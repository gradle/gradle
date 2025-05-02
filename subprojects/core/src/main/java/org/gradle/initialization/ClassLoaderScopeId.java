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

package org.gradle.initialization;

import org.jspecify.annotations.Nullable;

/**
 * Uniquely identifies a {@link org.gradle.api.internal.initialization.ClassLoaderScope} in
 * the {@link ClassLoaderScopeRegistry}.
 */
public interface ClassLoaderScopeId {

    @Nullable
    ClassLoaderScopeId getParent();

    String getName();

    @Override
    boolean equals(Object other);

    @Override
    int hashCode();
}
