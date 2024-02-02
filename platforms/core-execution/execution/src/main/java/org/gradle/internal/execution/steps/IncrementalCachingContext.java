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

package org.gradle.internal.execution.steps;

import org.gradle.internal.execution.caching.CachingState;

public class IncrementalCachingContext extends IncrementalChangesContext implements CachingContext {

    private final CachingState cachingState;

    public IncrementalCachingContext(IncrementalChangesContext parent, CachingState cachingState) {
        super(parent);
        this.cachingState = cachingState;
    }

    /**
     * The resolved state of caching for the work.
     */
    @Override
    public CachingState getCachingState() {
        return cachingState;
    }
}
