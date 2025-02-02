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

package org.gradle.cache.internal.locklistener;

import org.gradle.cache.FileLockReleasedSignal;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.util.function.Consumer;

@ServiceScope(Scope.Global.class)
public interface FileLockContentionHandler {
    void start(long lockId, Consumer<FileLockReleasedSignal> whenContended);

    void stop(long lockId);

    int reservePort();

    /**
     * Pings the lock owner with the give port to start the lock releasing
     * process in the owner. May not ping the owner if:
     * - The owner was already pinged about the given lock before and the lock release is in progress
     * - The ping through the underlying socket failed
     *
     * @return true if the owner was pinged in this call
     */
    boolean maybePingOwner(int port, long lockId, String displayName, long timeElapsed, @Nullable FileLockReleasedSignal signal);

    /**
     * Returns true if the handler is running and communication with other processes is still possible.
     */
    boolean isRunning();
}
