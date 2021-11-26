/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.initialization;

import javax.annotation.Nullable;

/**
 * Represents some data that has the same lifecycle as a {@link ClassLoaderScope}.
 */
public interface ClassLoaderScopeData {
    /**
     * Create a child of this data, if applicable.
     *
     * @return the child, or {@code null} if this data does not need children
     */
    @Nullable
    default ClassLoaderScopeData createChild() {
        return null;
    }
}
