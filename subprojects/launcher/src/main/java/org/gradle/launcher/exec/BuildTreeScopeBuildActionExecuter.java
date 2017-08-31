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

package org.gradle.launcher.exec;

import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.BuildSessionScopeServices;
import org.gradle.internal.service.scopes.BuildTreeScopeServices;

public class BuildTreeScopeBuildActionExecuter implements BuildActionExecuter<BuildActionParameters> {
    private final BuildActionExecuter<BuildActionParameters> delegate;

    public BuildTreeScopeBuildActionExecuter(BuildActionExecuter<BuildActionParameters> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object execute(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters, ServiceRegistry contextServices) {
        if (!(contextServices instanceof BuildSessionScopeServices)) {
            throw new IllegalArgumentException("Service registry must be of build session scope");
        }
        BuildTreeScopeServices buildTreeScopeServices = new BuildTreeScopeServices((BuildSessionScopeServices) contextServices);
        try {
            return delegate.execute(action, requestContext, actionParameters, buildTreeScopeServices);
        } finally {
            buildTreeScopeServices.close();
        }
    }
}
