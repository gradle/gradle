/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.tooling.provider.model.internal;

import org.gradle.api.NonNullApi;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

@NonNullApi
public interface ToolingModelParameterCarrier {

    /**
     * Returns a view of the original parameter object,
     * allowing access to its properties via methods available on the {@code viewType}.
     */
    Object getView(Class<?> viewType);

    /**
     * Returns a hash of the original parameter object.
     */
    HashCode getHash();

    @NonNullApi
    @ServiceScope(Scopes.BuildTree.class)
    interface Factory {

        ToolingModelParameterCarrier createCarrier(Object parameter);

    }
}
