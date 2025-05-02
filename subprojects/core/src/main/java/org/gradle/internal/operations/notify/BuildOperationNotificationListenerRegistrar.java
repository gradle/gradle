/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.operations.notify;

import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Mechanism by which the scan plugin registers for notifications.
 * <p>
 * One instance of this exists per build tree.
 * Only one listener may register.
 * Subsequent attempts yield exceptions.
 *
 * @since 4.0
 */
@UsedByScanPlugin("obtained from the root build's root project's service registry")
@ServiceScope(Scope.CrossBuildSession.class)
public interface BuildOperationNotificationListenerRegistrar {

    /**
     * The registered listener will receive notification for all build operations for the
     * current build execution, including those operations that started before the
     * listener was registered.
     *
     * @since 4.4
     */
    void register(BuildOperationNotificationListener listener);

}
