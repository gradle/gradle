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

package org.gradle.workers.internal;

import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

import java.util.Set;

public class SimpleActionExecutionSpec<T extends WorkParameters> {
    private final Class<? extends WorkAction<T>> implementationClass;
    private final T params;
    private final Set<Class<?>> additionalWhitelistedServices;

    public SimpleActionExecutionSpec(Class<? extends WorkAction<T>> implementationClass, T params, Set<Class<?>> additionalWhitelistedServices) {
        this.implementationClass = implementationClass;
        this.params = params;
        this.additionalWhitelistedServices = additionalWhitelistedServices;
    }

    public Set<Class<?>> getAdditionalWhitelistedServices() {
        return additionalWhitelistedServices;
    }

    public Class<? extends WorkAction<T>> getImplementationClass() {
        return implementationClass;
    }

    public T getParameters() {
        return params;
    }
}
