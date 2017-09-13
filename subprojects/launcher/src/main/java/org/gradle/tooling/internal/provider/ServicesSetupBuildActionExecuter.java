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

package org.gradle.tooling.internal.provider;

import org.gradle.StartParameter;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.SessionLifecycleListener;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.BuildSessionScopeServices;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildExecuter;

public class ServicesSetupBuildActionExecuter implements BuildExecuter {
    private final BuildActionExecuter<BuildActionParameters> delegate;
    private final GradleUserHomeScopeServiceRegistry userHomeServiceRegistry;

    public ServicesSetupBuildActionExecuter(BuildActionExecuter<BuildActionParameters> delegate, GradleUserHomeScopeServiceRegistry userHomeServiceRegistry) {
        this.delegate = delegate;
        this.userHomeServiceRegistry = userHomeServiceRegistry;
    }

    @Override
    public Object execute(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters, ServiceRegistry contextServices) {
        StartParameter startParameter = action.getStartParameter();
        ServiceRegistry userHomeServices = userHomeServiceRegistry.getServicesFor(startParameter.getGradleUserHomeDir());

        try {
            ServiceRegistry buildSessionScopeServices = new BuildSessionScopeServices(
                userHomeServices,
                startParameter,
                requestContext,
                actionParameters.getInjectedPluginClasspath()
            );
            try {
                SessionLifecycleListener sessionLifecycleListener = buildSessionScopeServices.get(ListenerManager.class).getBroadcaster(SessionLifecycleListener.class);
                try {
                    sessionLifecycleListener.afterStart();
                    return delegate.execute(action, requestContext, actionParameters, buildSessionScopeServices);
                } finally {
                    sessionLifecycleListener.beforeComplete();
                }
            } finally {
                CompositeStoppable.stoppable(buildSessionScopeServices).stop();
            }
        } finally {
            userHomeServiceRegistry.release(userHomeServices);
        }
    }
}
