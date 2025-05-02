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

package org.gradle.internal.build.event;

import org.gradle.api.provider.Provider;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.List;

@ServiceScope(Scope.Global.class)
public interface BuildEventListenerRegistryInternal extends BuildEventsListenerRegistry {
    /**
     * Subscribes the given listener to build operation completion events. Note that no start events are forwarded to the listener.
     */
    void onOperationCompletion(Provider<? extends BuildOperationListener> provider);

    void subscribe(Provider<?> provider);

    List<Provider<?>> getSubscriptions();
}
