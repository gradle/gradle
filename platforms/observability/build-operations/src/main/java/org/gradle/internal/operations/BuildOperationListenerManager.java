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

package org.gradle.internal.operations;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Manages listeners of build operations.
 *
 * There is one global listener for the life of the build runtime.
 * Listeners must be sure to remove themselves if they want to only listen for a single build.
 *
 * Listeners are notified in registration order.
 * Started and progress notifications are emitted in registration order,
 * while finished notifications are emitted in reverse registration order.
 *
 * Listeners will not receive progress notifications for events before they have received
 * the corresponding start notification or after they have received the corresponding finished notification.
 * Such notifications are just discarded for the listener.
 *
 * @since 3.5
 */
@ServiceScope(Scope.Global.class)
public interface BuildOperationListenerManager {

    void addListener(BuildOperationListener listener);

    void removeListener(BuildOperationListener listener);

    BuildOperationListener getBroadcaster();

}
