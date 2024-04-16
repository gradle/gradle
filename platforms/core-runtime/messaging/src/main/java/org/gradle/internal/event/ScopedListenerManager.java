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

package org.gradle.internal.event;

import org.gradle.api.NonNullApi;
import org.gradle.internal.service.AnnotatedServiceLifecycleHandler;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

@NonNullApi
@ServiceScope({Scope.Global.class, Scope.UserHome.class, Scope.BuildSession.class, Scope.BuildTree.class, Scope.Build.class})
public interface ScopedListenerManager extends ListenerManager, AnnotatedServiceLifecycleHandler {

    /**
     * Creates a child {@code ListenerManager}.
     * <p>
     * All events broadcast in the child will be received by the listeners registered in the parent.
     * However, the reverse is not true:
     * events broadcast in the parent are not received by the listeners in the children.
     * The child inherits the loggers of its parent, though these can be replaced.
     *
     * @return The child
     */
    ScopedListenerManager createChild(Class<? extends Scope> scope);

}
