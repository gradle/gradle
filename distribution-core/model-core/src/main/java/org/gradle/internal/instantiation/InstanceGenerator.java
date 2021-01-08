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

package org.gradle.internal.instantiation;

import org.gradle.api.Describable;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scopes.BuildTree.class)
public interface InstanceGenerator extends Instantiator {
    /**
     * Create a new instance of T with the given display name, using {@code parameters} as the construction parameters.
     *
     * @throws ObjectInstantiationException On failure to create the new instance.
     */
    <T> T newInstanceWithDisplayName(Class<? extends T> type, Describable displayName, Object... parameters) throws ObjectInstantiationException;
}
