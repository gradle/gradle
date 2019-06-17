/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.internal;

import org.gradle.internal.isolation.Isolatable;

public class IsolatedParametersActionExecutionSpec implements ActionExecutionSpec {
    private final String displayName;
    private final Class<?> implementationClass;
    private final Isolatable<?>[] isolatedParams;

    public IsolatedParametersActionExecutionSpec(Class<?> implementationClass, String displayName, Isolatable<?>[] isolatedParams) {
        this.implementationClass = implementationClass;
        this.displayName = displayName;
        this.isolatedParams = isolatedParams;
    }

    @Override
    public Class<?> getImplementationClass() {
        return implementationClass;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Object[] getParams() {
        Object[] params = new Object[isolatedParams.length];
        for (int i=0; i<isolatedParams.length; i++) {
            params[i] = isolatedParams[i].isolate();
        }
        return params;
    }
}
