/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.service;

import org.gradle.api.NonNullApi;
import org.gradle.internal.service.scopes.Scope;

/**
 * A registry validating that all registered services are annotated with a corresponding {@link Scope}.
 */
@NonNullApi
public class ScopedServiceRegistry extends DefaultServiceRegistry {

    public ScopedServiceRegistry(
        Class<? extends Scope> scope,
        String displayName,
        ServiceRegistry... parents
    ) {
        this(scope, false, displayName, parents);
    }

    public ScopedServiceRegistry(
        Class<? extends Scope> scope,
        boolean strict,
        String displayName,
        ServiceRegistry... parents
    ) {
        super(displayName, parents);
        addServiceValidator(scope, strict);
    }

    /**
     * Validator implements a special type of service ({@link AnnotatedServiceLifecycleHandler})
     * that gets notified about all existing and further service registrations.
     */
    private void addServiceValidator(Class<? extends Scope> scope, boolean strict) {
        add(new ServiceScopeValidator(scope, strict));
    }
}
