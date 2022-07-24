/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.resolver;

public enum MetadataFetchingCost {
    /**
     * Can return metadata immediately. It must <b>not</b> be used in case the metadata is missing,
     * even if it's cheap to tell it's missing. Use {@link #CHEAP} in that case.
     */
    FAST,
    /**
     * Can return metadata in a cheap way, or tell that it's missing in a cheap way. The difference with
     * {@link #FAST} is that we can use it for missing metadata.
     */
    CHEAP,
    /**
     * Cannot return metadata without an expensive call, typically involving parsing a descriptor
     * or accessing the network
     */
    EXPENSIVE;

    public boolean isFast() {
        return this == FAST;
    }

    public boolean isExpensive() {
        return this == EXPENSIVE;
    }
}
