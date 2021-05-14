/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.service.ServiceRegistry;

class DefaultBuildTreeContext implements BuildTreeContext {
    private final ServiceRegistry services;
    private boolean completed;

    public DefaultBuildTreeContext(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public BuildActionRunner.Result execute(BuildAction action) {
        if (completed) {
            throw new IllegalStateException("Cannot run more than one action for a build tree.");
        }
        try {
            BuildTreeLifecycleListener broadcaster = services.get(ListenerManager.class).getBroadcaster(BuildTreeLifecycleListener.class);
            broadcaster.afterStart();
            try {
                return services.get(BuildTreeActionExecutor.class).execute(action, this);
            } finally {
                broadcaster.beforeStop();
            }
        } finally {
            completed = true;
        }
    }
}
