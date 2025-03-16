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

package org.gradle.internal.actor;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scope.Build.class)
public interface ActorFactory {
    /**
     * Creates an asynchronous actor for the given target object.
     *
     * @param target The target object.
     * @return The actor.
     */
    Actor createActor(Object target);

    /**
     * Creates a synchronous actor for the given target object.
     *
     * @param target The target object.
     * @return The actor.
     */
    Actor createBlockingActor(Object target);
}
