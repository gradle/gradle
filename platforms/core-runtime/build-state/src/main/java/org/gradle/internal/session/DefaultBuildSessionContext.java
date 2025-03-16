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

package org.gradle.internal.session;

import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.service.ServiceRegistry;

class DefaultBuildSessionContext implements BuildSessionContext {
    private final ServiceRegistry sessionScopeServices;
    private boolean completed;

    public DefaultBuildSessionContext(ServiceRegistry sessionScopeServices) {
        this.sessionScopeServices = sessionScopeServices;
    }

    @Override
    public ServiceRegistry getServices() {
        return sessionScopeServices;
    }

    @Override
    public BuildActionRunner.Result execute(BuildAction action) {
        if (completed) {
            throw new IllegalStateException("Cannot run more than one action for a session.");
        }
        try {
            BuildSessionLifecycleListener sessionLifecycleListener = sessionScopeServices.get(ListenerManager.class).getBroadcaster(BuildSessionLifecycleListener.class);
            sessionLifecycleListener.afterStart();
            try {
                return sessionScopeServices.get(BuildSessionActionExecutor.class).execute(action, this);
            } finally {
                sessionLifecycleListener.beforeComplete();
            }
        } finally {
            completed = true;
        }
    }
}
