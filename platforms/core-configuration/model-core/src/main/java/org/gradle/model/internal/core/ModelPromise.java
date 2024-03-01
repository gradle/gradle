/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.internal.core;

import org.gradle.model.internal.type.ModelType;

public interface ModelPromise {

    <T> boolean canBeViewedAs(ModelType<T> type);

    // These methods return strings rather than types because it may be more complicated than what is able to be expressed via a ModelType.
    // Also, we don't want to encourage compatibility checking occurring by looping through such types as we have more options for optimising the compatibility check internally.
    // Also also, these methods are only called for reporting so values should typically not be precomputed.
    Iterable<String> getTypeDescriptions(MutableModelNode node);
}
