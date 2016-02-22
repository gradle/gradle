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

package org.gradle.launcher.exec;

import org.gradle.internal.composite.CompositeBuildController;
import org.gradle.internal.service.ServiceRegistry;

public class DefaultCompositeBuildController implements CompositeBuildController {
    private final ServiceRegistry buildScopeServices;
    private Object result;
    private boolean hasResult;

    public DefaultCompositeBuildController(ServiceRegistry buildScopeServices) {
        this.buildScopeServices = buildScopeServices;
    }

    @Override
    public ServiceRegistry getBuildScopeServices() {
        return buildScopeServices;
    }

    @Override
    public boolean hasResult() {
        return hasResult;
    }

    @Override
    public Object getResult() {
        if (!hasResult) {
            throw new IllegalStateException("No result has been provided for this build action.");
        }
        return result;
    }

    @Override
    public void setResult(Object result) {
        this.hasResult = true;
        this.result = result;
    }
}
