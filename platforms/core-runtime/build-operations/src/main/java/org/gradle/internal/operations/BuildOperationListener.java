/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scope.Global;

/**
 * A listener that is notified as build operations are executed.
 *
 * Listeners must be registered via {@link BuildOperationListenerManager}, not ListenerManager.
 *
 * Unlike ListenerManager bound listeners, build operation listener signalling is not synchronized.
 * Implementations must take care to be threadsafe.
 *
 * Related signals are guaranteed to be serialized.
 * That is, a listener will not concurrently be notified of the same operation starting and finishing.
 *
 * @since 3.5
 */
@EventScope(Global.class)
public interface BuildOperationListener {

    void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent);

    void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent);

    void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent);

}
