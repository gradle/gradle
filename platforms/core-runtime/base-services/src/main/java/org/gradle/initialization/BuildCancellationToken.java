/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Propagates notification that the build should be cancelled.
 */
@ServiceScope({Scope.Global.class, Scope.BuildSession.class, Scope.Build.class})
public interface BuildCancellationToken {

    boolean isCancellationRequested();

    void cancel();

    /**
     * @return current state of cancellation request before callback was added.
     */
    boolean addCallback(Runnable cancellationHandler);

    /**
     * Removes a callback called when cancellation request happens.
     *
     * @param cancellationHandler removed callback.
     */
    void removeCallback(Runnable cancellationHandler);

}
