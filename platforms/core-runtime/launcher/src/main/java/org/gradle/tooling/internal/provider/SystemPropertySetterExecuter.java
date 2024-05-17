/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;

import java.util.Properties;

public class SystemPropertySetterExecuter implements BuildActionExecuter<BuildActionParameters, BuildRequestContext> {

    private final BuildActionExecuter<BuildActionParameters, BuildRequestContext> delegate;

    public SystemPropertySetterExecuter(BuildActionExecuter<BuildActionParameters, BuildRequestContext> delegate) {
        this.delegate = delegate;
    }

    @Override
    public BuildActionResult execute(BuildAction action, BuildActionParameters actionParameters, BuildRequestContext buildRequestContext) {
        Properties originalProperties = System.getProperties();
        Properties updatedProperties = new Properties();
        updatedProperties.putAll(originalProperties);
        updatedProperties.putAll(actionParameters.getSystemProperties());
        System.setProperties(updatedProperties);
        try {
            return delegate.execute(action, actionParameters, buildRequestContext);
        } finally {
            System.setProperties(originalProperties);
        }
    }
}
