/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.attributes;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;

import java.util.Map;

public interface AttributeContainerInternal extends AttributeContainer {

    /**
     * Returns an immutable copy of this attribute set. Implementations are not required to return a distinct instance for each call.
     * Changes to this set are <em>not</em> reflected in the immutable copy.
     *
     * @return an immutable view of this container.
     */
    ImmutableAttributes asImmutable();

    /**
     * Returns a copy of this attribute container as a map. This is an expensive
     * operation which should be limited to cases like diagnostics which are worthy of time.
     * @return a copy of this container, as a map.
     */
    Map<Attribute<?>, ?> asMap();
}
