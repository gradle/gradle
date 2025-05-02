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

package org.gradle.launcher.daemon.server.expiry;

import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scope.Global;

/**
 * Represents an event where a daemon expiration condition was detected and the daemon
 * should now stop.
 */
@EventScope(Global.class)
public interface DaemonExpirationListener {
    /**
     * Will be fired when the daemon expiration event occurs.
     *
     * @param result The result object from the triggered condition
     */
    void onExpirationEvent(DaemonExpirationResult result);
}
