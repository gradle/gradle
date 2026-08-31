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

package org.gradle.internal.collect;

import java.util.Set;

/**
 * A generic {@link Set} decorator over {@link InterceptingCollection}. {@code Set} adds no methods to
 * {@code Collection}, so this only fixes the type.
 */
public class InterceptingSet<E> extends InterceptingCollection<E, Set<E>> implements Set<E> {

    public InterceptingSet(Set<E> delegate, Interceptor interceptor) {
        super(delegate, interceptor);
    }
}
