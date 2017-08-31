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

package org.gradle.internal.progress;

import org.gradle.internal.event.ListenerManager;

public class DefaultBuildOperationListenerManager implements BuildOperationListenerManager {

    private final ListenerManager listenerManager;

    public DefaultBuildOperationListenerManager(ListenerManager listenerManager) {
        this.listenerManager = listenerManager;
    }

    @Override
    public void addListener(BuildOperationListener listener) {
        listenerManager.addListener(listener);
    }

    @Override
    public void removeListener(BuildOperationListener listener) {
        listenerManager.removeListener(listener);
    }
}
