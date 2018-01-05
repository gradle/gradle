/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal;

import java.io.Serializable;

/**
 * Used to represent a {@code null} reference as a type. This is useful when doing transformations
 * on parameter types when some parameters may be unreadable due to being {@code null}. It can act
 * as a placeholder for a {@code null} reference.
 * <p>
 * This is used by the Worker API to communicate to the {@link DependencyInjectingInstantiator}
 * of when a {@code null} reference should be used in place of a constructor parameter.
 */
public final class NullReference implements Serializable {
    private static final NullReference INSTANCE = new NullReference();

    /**
     * Do not allow public construction.
     */
    private NullReference() {
    }

    /**
     * Returns the single {@link NullReference} instance maintained by the class.
     *
     * @return Singleton instance.
     */
    public static NullReference get() {
        return INSTANCE;
    }
}
